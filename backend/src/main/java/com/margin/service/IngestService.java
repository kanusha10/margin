package com.margin;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Clones a repo, splits source files into overlapping line windows, embeds them and stores the vectors. */
@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final int WINDOW = 50, OVERLAP = 10;

    private final RepoService repos;
    private final VectorStore store;
    private final GeminiClient gemini;
    private final int maxChunks, batchSize;
    private final long delayMs;

    public IngestService(RepoService repos, VectorStore store, GeminiClient gemini,
                         @Value("${app.max-chunks}") int maxChunks,
                         @Value("${app.embed-batch:25}") int batchSize,
                         // Pacing is handled by GeminiClient now, so this extra pause is optional.
                         @Value("${app.embed-delay-ms:0}") long delayMs) {
        this.repos = repos;
        this.store = store;
        this.gemini = gemini;
        this.maxChunks = maxChunks;
        this.batchSize = Math.max(1, batchSize);
        this.delayMs = delayMs;
    }

    @Async
    public void ingest(long id, String url) {
        try {
            Path dir = repos.dir(id);
            repos.update(id, "CLONING", "Cloning the repository");
            RepoFiles.purge(dir);
            Files.createDirectories(dir.getParent());
            try (Git ignored = Git.cloneRepository().setURI(url).setDirectory(dir.toFile()).setDepth(1).call()) {
                log.info("Cloned {}", url);
            }
            RepoFiles.purge(dir.resolve(".git"));

            repos.update(id, "INDEXING", "Reading source files");
            List<VectorStore.Chunk> chunks = new ArrayList<>();
            int files = 0;
            for (Path p : RepoFiles.sourceFiles(dir)) {
                List<VectorStore.Chunk> c = chunk(dir, p);
                if (c.isEmpty()) continue;
                files++;
                chunks.addAll(c);
                if (chunks.size() >= maxChunks) { chunks = new ArrayList<>(chunks.subList(0, maxChunks)); break; }
            }
            if (chunks.isEmpty()) throw new IllegalStateException("No readable source files were found in this repository.");

            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<VectorStore.Chunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
                String progress = "Embedding code: " + i + " of " + chunks.size() + " chunks";
                repos.update(id, "INDEXING", progress);
                List<String> texts = batch.stream().map(c -> {
                    String t = "File: " + c.path() + "\n" + c.content();
                    return t.length() > 8000 ? t.substring(0, 8000) : t;
                }).toList();
                // If Gemini's free-tier limit makes us wait, show that next to the progress text.
                store.insert(id, batch, gemini.embed(texts, "RETRIEVAL_DOCUMENT",
                        note -> repos.update(id, "INDEXING", progress + ". " + note)));
                if (delayMs > 0) Thread.sleep(delayMs);
            }
            repos.finish(id, files, chunks.size());
            log.info("Indexed repo {}: {} files, {} chunks", id, files, chunks.size());
        } catch (Exception e) {
            log.error("Ingest failed for repo {}", id, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            repos.update(id, "FAILED", msg.length() > 300 ? msg.substring(0, 300) : msg);
        }
    }

    private List<VectorStore.Chunk> chunk(Path root, Path file) {
        List<String> lines;
        try { lines = Files.readAllLines(file, StandardCharsets.UTF_8); }
        catch (IOException e) { return List.of(); }   // binary or non UTF-8: skip
        String rel = RepoFiles.rel(root, file);
        List<VectorStore.Chunk> out = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += WINDOW - OVERLAP) {
            int end = Math.min(start + WINDOW, lines.size());
            String text = String.join("\n", lines.subList(start, end));
            if (!text.isBlank()) out.add(new VectorStore.Chunk(rel, start + 1, end, text));
            if (end == lines.size()) break;
        }
        return out;
    }
}
