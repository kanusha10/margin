package com.margin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin wrapper over the Gemini REST API: embeddings and generateContent with function calling.
 *
 * Built for the free tier, which allows only a handful of requests per minute and per day:
 *  1. Every call first reserves a slot in a local sliding window (gemini.chat-rpm / gemini.embed-rpm),
 *     so most 429s never happen. Each model has its own window.
 *  2. If Gemini still answers 429, the Retry-After header or the retryDelay in the error body is honoured,
 *     and every thread sharing that model pauses together.
 *  3. A per-day quota error is not retried. It fails fast with a clear message, or switches to
 *     gemini.fallback-chat-model when one is configured.
 *  4. 5xx and network errors are retried with jittered exponential backoff.
 *  5. Whenever a call has to wait, the WaitListener is told so the UI can show it.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final long WINDOW_MS = 60_000L;

    /** Receives short, human readable text while a call is waiting on Gemini's limits. */
    public interface WaitListener { void waiting(String message); }
    public static final WaitListener SILENT = m -> { };

    /** The daily free quota is gone (or the model has no free quota). Retrying will not help. */
    public static class QuotaExhaustedException extends IllegalStateException {
        public QuotaExhaustedException(String message) { super(message); }
    }

    private record Quota(boolean daily, boolean none, long retryAfterMs) {}

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper;
    private final String apiKey, chatModel, embedModel, fallbackModel;
    private final int dim, chatRpm, maxRetries;
    private final long maxWaitMs;
    private final Map<String, QuotaThrottle> throttles = new ConcurrentHashMap<>();
    private final Map<String, float[]> queryCache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, float[]> e) { return size() > 200; }
    });

    public GeminiClient(ObjectMapper mapper,
                        @Value("${gemini.api-key}") String apiKey,
                        @Value("${gemini.chat-model}") String chatModel,
                        @Value("${gemini.embed-model}") String embedModel,
                        @Value("${gemini.embed-dim}") int dim,
                        @Value("${gemini.fallback-chat-model:}") String fallbackModel,
                        @Value("${gemini.chat-rpm:8}") int chatRpm,
                        @Value("${gemini.embed-rpm:80}") int embedRpm,
                        @Value("${gemini.max-wait-seconds:120}") int maxWaitSeconds,
                        @Value("${gemini.max-retries:5}") int maxRetries) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embedModel = embedModel;
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        this.dim = dim;
        this.chatRpm = chatRpm;
        this.maxRetries = maxRetries;
        this.maxWaitMs = maxWaitSeconds * 1000L;
        throttles.put(embedModel, new QuotaThrottle(embedRpm, WINDOW_MS));
    }

    public boolean hasKey() { return apiKey != null && !apiKey.isBlank(); }

    public List<float[]> embed(List<String> texts, String taskType) { return embed(texts, taskType, SILENT); }

    /** taskType: RETRIEVAL_DOCUMENT for code chunks, CODE_RETRIEVAL_QUERY for questions. */
    public List<float[]> embed(List<String> texts, String taskType, WaitListener listener) {
        boolean cacheable = texts.size() == 1 && !"RETRIEVAL_DOCUMENT".equals(taskType);
        String cacheKey = cacheable ? taskType + "\u0000" + texts.get(0) : null;
        if (cacheable) {
            float[] hit = queryCache.get(cacheKey);
            if (hit != null) return List.of(hit);   // identical question text: no quota used
        }

        ObjectNode body = mapper.createObjectNode();
        ArrayNode reqs = body.putArray("requests");
        for (String t : texts) {
            ObjectNode r = reqs.addObject();
            r.put("model", "models/" + embedModel);
            r.putObject("content").putArray("parts").addObject().put("text", t);
            r.put("taskType", taskType);
            r.put("outputDimensionality", dim);
        }
        // Each text in a batch is counted as one request, which is the safe way to stay under the limit.
        JsonNode res = post(embedModel, ":batchEmbedContents", body, texts.size(), listener);
        List<float[]> out = new ArrayList<>();
        for (JsonNode e : res.path("embeddings")) {
            JsonNode values = e.path("values");
            float[] v = new float[values.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) values.get(i).asDouble();
            out.add(v);
        }
        if (out.size() != texts.size()) throw new IllegalStateException("Embedding API returned an unexpected result");
        if (cacheable) queryCache.put(cacheKey, out.get(0));
        return out;
    }

    public JsonNode generate(String system, ArrayNode contents, JsonNode tools) {
        return generate(system, contents, tools, SILENT);
    }

    public JsonNode generate(String system, ArrayNode contents, JsonNode tools, WaitListener listener) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        body.set("contents", contents);
        if (tools != null) body.set("tools", tools);
        body.putObject("generationConfig").put("temperature", 0.2);
        try {
            return post(chatModel, ":generateContent", body, 1, listener);
        } catch (QuotaExhaustedException e) {
            if (fallbackModel.isEmpty() || fallbackModel.equals(chatModel)) throw e;
            log.warn("{} is out of quota, switching to {}", chatModel, fallbackModel);
            listener.waiting("Quota for " + chatModel + " is used up, switching to " + fallbackModel);
            return post(fallbackModel, ":generateContent", body, 1, listener);
        }
    }

    private QuotaThrottle throttleFor(String model) {
        return throttles.computeIfAbsent(model, m -> new QuotaThrottle(chatRpm, WINDOW_MS));
    }

    private JsonNode post(String model, String method, JsonNode body, int units, WaitListener listener) {
        if (!hasKey()) throw new IllegalStateException("GEMINI_API_KEY is not set on the backend.");
        QuotaThrottle throttle = throttleFor(model);
        long deadline = System.currentTimeMillis() + maxWaitMs;

        for (int attempt = 0; ; attempt++) {
            awaitSlot(throttle, units, deadline, listener);
            try {
                return http.post().uri(BASE + model + method)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body).retrieve().body(JsonNode.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                Quota q = parseQuota(e);
                if (q.none())
                    throw new QuotaExhaustedException("Your Gemini key has no free quota for " + model
                            + ". Pick another model in gemini.chat-model or gemini.embed-model.");
                if (q.daily())
                    throw new QuotaExhaustedException("The free daily Gemini quota for " + model
                            + " is used up. It resets once a day (midnight Pacific time). Try again later.");
                if (attempt >= maxRetries)
                    throw new IllegalStateException("Gemini is still rate limiting this key after " + maxRetries
                            + " retries. Wait a minute and try again.");
                long wait = q.retryAfterMs() > 0 ? q.retryAfterMs() + jitter() : backoff(attempt);
                if (wait > maxWaitMs)
                    throw new IllegalStateException("Gemini asked us to wait " + (wait / 1000) + " seconds before the next request. Try again then.");
                log.warn("Gemini 429 for {}, pausing {} ms (attempt {})", model, wait, attempt + 1);
                throttle.blockFor(wait);
                deadline = Math.max(deadline, System.currentTimeMillis() + wait + 10_000L);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt >= maxRetries)
                    throw new IllegalStateException("Gemini is unavailable right now. Try again in a minute.");
                long wait = backoff(attempt);
                log.warn("Gemini unavailable ({}), retrying in {} ms", e.getClass().getSimpleName(), wait);
                listener.waiting("Gemini is busy, retrying in " + seconds(wait) + "s");
                sleep(wait);
            } catch (HttpClientErrorException e) {
                throw new IllegalStateException("Gemini rejected the request: " + errorMessage(e));
            }
        }
    }

    /** Blocks until the throttle grants a slot, telling the listener while it waits. */
    private void awaitSlot(QuotaThrottle throttle, int units, long deadline, WaitListener listener) {
        for (;;) {
            long wait = throttle.reserve(units);
            if (wait <= 0) return;
            if (System.currentTimeMillis() + wait > deadline)
                throw new IllegalStateException("Gemini's free-tier rate limit is busy. Try again in about " + seconds(wait) + " seconds.");
            if (wait >= 1500) listener.waiting("Waiting " + seconds(wait) + "s for the Gemini free-tier rate limit");
            sleep(Math.min(wait, 5000));
        }
    }

    private Quota parseQuota(HttpClientErrorException e) {
        boolean daily = false, none = false;
        long retryMs = 0;
        var headers = e.getResponseHeaders();
        String header = headers == null ? null : headers.getFirst("Retry-After");
        if (header != null) {
            try { retryMs = Long.parseLong(header.trim()) * 1000L; } catch (NumberFormatException ignored) { /* HTTP date form: ignore */ }
        }
        try {
            JsonNode err = mapper.readTree(e.getResponseBodyAsString()).path("error");
            String msg = err.path("message").asText("").toLowerCase();
            if (msg.contains("limit: 0")) none = true;
            if (msg.contains("per day") || msg.contains("daily")) daily = true;
            for (JsonNode d : err.path("details")) {
                for (JsonNode v : d.path("violations")) {
                    String id = (v.path("quotaId").asText("") + v.path("quotaMetric").asText("")).toLowerCase();
                    if (id.contains("perday")) daily = true;
                }
                String delay = d.path("retryDelay").asText("");   // looks like "37s" or "37.5s"
                if (delay.endsWith("s")) {
                    try { retryMs = Math.max(retryMs, (long) (Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000)); }
                    catch (NumberFormatException ignored) { /* keep what we have */ }
                }
            }
        } catch (Exception ignored) { /* body was not JSON: fall back to plain backoff */ }
        return new Quota(daily, none, retryMs);
    }

    private String errorMessage(HttpClientErrorException e) {
        try {
            String m = mapper.readTree(e.getResponseBodyAsString()).path("error").path("message").asText("");
            if (!m.isBlank()) return m.length() > 300 ? m.substring(0, 300) : m;
        } catch (Exception ignored) { /* not JSON */ }
        String body = e.getResponseBodyAsString();
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static long backoff(int attempt) { return Math.min(60_000L, 2000L << Math.min(attempt, 5)) + jitter(); }
    private static long jitter() { return ThreadLocalRandom.current().nextLong(0, 1000); }
    private static long seconds(long ms) { return Math.max(1, (ms + 999) / 1000); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted"); }
    }
}
