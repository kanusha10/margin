package com.margin;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Sliding-window limiter kept in memory: at most `max` hits per `window` for each key. */
@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public synchronized boolean tryAcquire(String key, int max, Duration window) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > window.toMillis()) q.pollFirst();
        if (q.size() >= max) return false;
        q.addLast(now);
        return true;
    }
}
