package com.margin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/repos")
public class RepoController {
    public record AddRequest(String url) {}
    public record ChatRequest(String question, List<Turn> history) {}

    private static final Pattern GITHUB = Pattern.compile("^https://(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    private final RepoService repos;
    private final IngestService ingest;
    private final AgentService agent;
    private final GeminiClient gemini;
    private final RateLimiter limiter;
    private final int maxRepos, chatsPerHour;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public RepoController(RepoService repos, IngestService ingest, AgentService agent, GeminiClient gemini, RateLimiter limiter,
                          @Value("${app.max-repos-per-user}") int maxRepos,
                          @Value("${app.chats-per-hour}") int chatsPerHour) {
        this.repos = repos;
        this.ingest = ingest;
        this.agent = agent;
        this.gemini = gemini;
        this.limiter = limiter;
        this.maxRepos = maxRepos;
        this.chatsPerHour = chatsPerHour;
    }

    @GetMapping
    public List<Repo> list(@AuthenticationPrincipal Long userId) { return repos.list(userId); }

    @PostMapping
    public Repo add(@AuthenticationPrincipal Long userId, @RequestBody AddRequest req) {
        if (!gemini.hasKey())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY is not set. Set it and restart the backend.");
        Matcher m = GITHUB.matcher(req.url() == null ? "" : req.url().trim());
        if (!m.matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a public GitHub URL like https://github.com/owner/repo");
        String url = "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git";
        var existing = repos.findByUrl(userId, url);
        if (existing.isPresent()) return existing.get();
        if (repos.count(userId) >= maxRepos)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You can index up to " + maxRepos + " repositories. Remove one to add another.");
        Repo repo = repos.create(userId, m.group(1) + "/" + m.group(2), url);
        ingest.ingest(repo.id(), url);
        return repo;
    }

    @DeleteMapping("/{id}")
    public void remove(@AuthenticationPrincipal Long userId, @PathVariable long id) { repos.delete(id, userId); }

    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@AuthenticationPrincipal Long userId, @PathVariable long id, @RequestBody ChatRequest req) {
        Repo repo = repos.find(id, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
        if (!"READY".equals(repo.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This repository is not indexed yet.");
        if (req.question() == null || req.question().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question first.");
        if (!limiter.tryAcquire("chat:" + userId, chatsPerHour, Duration.ofHours(1)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used all " + chatsPerHour + " questions for this hour. Try again later.");

        SseEmitter emitter = new SseEmitter(300_000L);
        pool.submit(() -> {
            try {
                agent.run(repo, req.history() == null ? List.of() : req.history(), req.question(), (type, data) -> send(emitter, type, data));
            } catch (Exception e) {
                send(emitter, "error", Map.of("message", e.getMessage() == null ? "Something went wrong." : e.getMessage()));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String type, Object data) {
        try {
            emitter.send(SseEmitter.event().name(type).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) { /* client went away */ }
    }
}
