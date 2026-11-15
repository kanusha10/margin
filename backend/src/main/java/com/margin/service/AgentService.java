package com.margin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * The agent loop: ask the model, run whatever tools it requests, feed the results back,
 * and repeat until it answers in plain text. Every tool call is streamed to the UI as a "step".
 */
@Service
public class AgentService {
    private record ToolResult(String text, String summary) {}

    private static final String TOOLS = """
        [{"functionDeclarations":[
          {"name":"search_code","description":"Semantic search over the repository. Accepts natural language or identifiers. Returns the most relevant code chunks with file paths and line ranges.",
           "parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"What to look for"}},"required":["query"]}},
          {"name":"grep_code","description":"Case-insensitive literal text search across all files. Best for exact identifiers, annotations or config keys. Returns path:line: text.",
           "parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"}},"required":["text"]}},
          {"name":"list_files","description":"List files and folders directly inside a directory. Use an empty string for the repository root.",
           "parameters":{"type":"OBJECT","properties":{"directory":{"type":"STRING"}},"required":["directory"]}},
          {"name":"read_file","description":"Read a file with line numbers. Returns at most 200 lines starting at start_line (default 1).",
           "parameters":{"type":"OBJECT","properties":{"path":{"type":"STRING"},"start_line":{"type":"INTEGER"}},"required":["path"]}}
        ]}]
        """;

    private final GeminiClient gemini;
    private final VectorStore store;
    private final RepoService repos;
    private final ObjectMapper mapper;
    private final int maxSteps;
    private final JsonNode tools;

    public AgentService(GeminiClient gemini, VectorStore store, RepoService repos, ObjectMapper mapper,
                        @Value("${app.max-agent-steps}") int maxSteps) {
        this.gemini = gemini;
        this.store = store;
        this.repos = repos;
        this.mapper = mapper;
        this.maxSteps = maxSteps;
        try { this.tools = mapper.readTree(TOOLS); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public void run(Repo repo, List<Turn> history, String question, BiConsumer<String, Object> emit) {
        Path root = repos.dir(repo.id());
        String system = "You are Margin, an assistant that answers questions about the source code of the repository "
                + repo.name() + ". Ground every answer in the code: use search_code first, then read_file to confirm "
                + "details, and use grep_code for exact identifiers. Follow the call chain across files when the question "
                + "is about a flow. Never invent file names or code you have not seen. Cite locations as `path:line`. "
                + "Answer in concise Markdown, with short code excerpts only when they help. If the repository does not "
                + "contain the answer, say so plainly.";

        ArrayNode contents = mapper.createArrayNode();
        for (Turn t : history.subList(Math.max(0, history.size() - 12), history.size())) {
            if (t.text() == null || t.text().isBlank()) continue;
            ObjectNode c = contents.addObject();
            c.put("role", "model".equals(t.role()) ? "model" : "user");
            c.putArray("parts").addObject().put("text", t.text());
        }
        ObjectNode q = contents.addObject();
        q.put("role", "user");
        q.putArray("parts").addObject().put("text", question);

        Set<String> sources = new LinkedHashSet<>();
        // Free-tier waits are streamed to the UI as "notice" events so the user knows why it is pausing.
        GeminiClient.WaitListener wait = msg -> emit.accept("notice", Map.of("message", msg));

        for (int step = 0; step < maxSteps; step++) {
            JsonNode content = candidate(gemini.generate(system, contents, tools, wait));
            List<JsonNode> calls = new ArrayList<>();
            for (JsonNode part : content.path("parts")) if (part.has("functionCall")) calls.add(part.get("functionCall"));

            if (calls.isEmpty()) {
                answer(content, sources, emit);
                return;
            }
            contents.add(content);
            ObjectNode reply = contents.addObject();
            reply.put("role", "user");
            ArrayNode replyParts = reply.putArray("parts");
            for (JsonNode call : calls) {
                String name = call.path("name").asText();
                JsonNode args = call.path("args");
                ToolResult r = execute(repo.id(), root, name, args, sources, wait);
                emit.accept("step", Map.of("tool", name, "args", args, "summary", r.summary()));
                ObjectNode fr = replyParts.addObject().putObject("functionResponse");
                fr.put("name", name);
                fr.putObject("response").put("result", r.text());
            }
        }

        // Step budget used up: force a final answer from what has been gathered.
        ObjectNode nudge = contents.addObject();
        nudge.put("role", "user");
        nudge.putArray("parts").addObject().put("text", "Stop searching and answer now using what you have found.");
        answer(candidate(gemini.generate(system, contents, null, wait)), sources, emit);
    }

    private JsonNode candidate(JsonNode res) {
        JsonNode content = res.path("candidates").path(0).path("content");
        if (content.isMissingNode() || !content.has("parts"))
            throw new IllegalStateException("The model returned no answer (it may have been blocked). Try rephrasing the question.");
        return content;
    }

    private void answer(JsonNode content, Set<String> sources, BiConsumer<String, Object> emit) {
        StringBuilder text = new StringBuilder();
        for (JsonNode p : content.path("parts"))
            if (p.has("text") && !p.path("thought").asBoolean(false)) text.append(p.get("text").asText());
        List<String> list = new ArrayList<>(sources);
        emit.accept("answer", Map.of("text", text.toString().trim(), "sources", list.subList(0, Math.min(8, list.size()))));
    }

    private ToolResult execute(long repoId, Path root, String name, JsonNode a, Set<String> sources, GeminiClient.WaitListener wait) {
        try {
            return switch (name) {
                case "search_code" -> searchCode(repoId, a.path("query").asText(), sources, wait);
                case "grep_code" -> grep(root, a.path("text").asText());
                case "list_files" -> listFiles(root, a.path("directory").asText(""));
                case "read_file" -> readFile(root, a.path("path").asText(), a.path("start_line").asInt(1), sources);
                default -> new ToolResult("Unknown tool: " + name, "unknown tool");
            };
        } catch (Exception e) {
            return new ToolResult("Tool failed: " + e.getMessage(), "failed");
        }
    }

    private ToolResult searchCode(long repoId, String query, Set<String> sources, GeminiClient.WaitListener wait) {
        float[] v = gemini.embed(List.of(query), "CODE_RETRIEVAL_QUERY", wait).get(0);
        List<VectorStore.Hit> hits = store.search(repoId, v, 6);
        StringBuilder sb = new StringBuilder();
        for (VectorStore.Hit h : hits) {
            sources.add(h.path() + ":" + h.start() + "-" + h.end());
            sb.append("### ").append(h.path()).append(" (lines ").append(h.start()).append("-").append(h.end())
              .append(", similarity ").append(String.format("%.2f", h.score())).append(")\n")
              .append(h.content()).append("\n\n");
        }
        return new ToolResult(hits.isEmpty() ? "No results." : sb.toString(), hits.size() + " matches");
    }

    private ToolResult grep(Path root, String text) throws IOException {
        String needle = text.toLowerCase();
        List<String> found = new ArrayList<>();
        outer:
        for (Path f : RepoFiles.sourceFiles(root)) {
            List<String> lines;
            try { lines = Files.readAllLines(f); } catch (IOException e) { continue; }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).toLowerCase().contains(needle)) {
                    String l = lines.get(i).strip();
                    found.add(RepoFiles.rel(root, f) + ":" + (i + 1) + ": " + (l.length() > 160 ? l.substring(0, 160) : l));
                    if (found.size() >= 25) break outer;
                }
            }
        }
        return new ToolResult(found.isEmpty() ? "No matches." : String.join("\n", found), found.size() + " matches");
    }

    private ToolResult listFiles(Path root, String directory) throws IOException {
        Path dir = RepoFiles.resolve(root, directory);
        if (!Files.isDirectory(dir))
            return new ToolResult("Not a directory: " + directory, "not found");
        try (Stream<Path> s = Files.list(dir)) {
            List<String> names = s.filter(p -> !RepoFiles.SKIP_DIRS.contains(p.getFileName().toString()))
                    .map(p -> p.getFileName() + (Files.isDirectory(p) ? "/" : "")).sorted().toList();
            return new ToolResult(String.join("\n", names), names.size() + " entries");
        }
    }

    private ToolResult readFile(Path root, String path, int startLine, Set<String> sources) throws IOException {
        Path f = RepoFiles.resolve(root, path);
        if (!Files.isRegularFile(f))
            return new ToolResult("File not found: " + path + ". Use list_files or search_code to find the right path.", "not found");
        List<String> lines = Files.readAllLines(f);
        int start = Math.max(1, startLine), end = Math.min(lines.size(), start + 199);
        if (start > lines.size())
            return new ToolResult("The file only has " + lines.size() + " lines.", "out of range");
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) sb.append(String.format("%4d  %s%n", i, lines.get(i - 1)));
        if (end < lines.size()) sb.append("... file continues, call read_file with start_line=").append(end + 1).append('\n');
        sources.add(RepoFiles.rel(root, f) + ":" + start + "-" + end);
        return new ToolResult(sb.toString(), "lines " + start + " to " + end + " of " + lines.size());
    }
}
