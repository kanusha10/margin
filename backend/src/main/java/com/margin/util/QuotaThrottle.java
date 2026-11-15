package com.margin;

import java.util.ArrayDeque;

/**
 * Tracks one Gemini quota bucket, for example "chat requests per minute".
 *
 * Callers reserve units before every request. If the window is full, reserve() returns how many
 * milliseconds to wait. When Gemini itself answers 429, blockFor() pauses everybody who shares the bucket,
 * so one rejected call does not trigger a stampede of retries from other threads.
 */
public class QuotaThrottle {
    private final int limit;
    private final long windowMs;
    private final ArrayDeque<long[]> hits = new ArrayDeque<>();   // {timestamp, units}
    private int used;
    private long blockedUntil;

    public QuotaThrottle(int limit, long windowMs) {
        this.limit = Math.max(1, limit);
        this.windowMs = windowMs;
    }

    /** Returns 0 when a slot was reserved, otherwise the milliseconds to wait before asking again. */
    public synchronized long reserve(int units) {
        long now = System.currentTimeMillis();
        while (!hits.isEmpty() && now - hits.peekFirst()[0] >= windowMs) used -= (int) hits.pollFirst()[1];
        if (now < blockedUntil) return blockedUntil - now;

        int need = Math.min(Math.max(1, units), limit);
        if (used + need <= limit) {
            hits.addLast(new long[]{now, need});
            used += need;
            return 0;
        }

        // Work out when enough old hits will have expired to make room.
        int freed = limit - used;
        long wait = 0;
        for (long[] h : hits) {
            freed += (int) h[1];
            wait = h[0] + windowMs - now;
            if (freed >= need) break;
        }
        return Math.max(wait, 100);
    }

    /** Called after a 429 so every caller backs off for at least `ms`. */
    public synchronized void blockFor(long ms) {
        blockedUntil = Math.max(blockedUntil, System.currentTimeMillis() + ms);
    }
}
