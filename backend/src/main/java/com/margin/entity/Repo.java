package com.margin;

import java.time.Instant;

public record Repo(long id, String name, String url, String status, String message,
                   int fileCount, int chunkCount, Instant createdAt) {}
