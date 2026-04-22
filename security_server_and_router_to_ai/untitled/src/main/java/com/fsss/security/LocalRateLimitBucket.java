package com.fsss.security;

import java.time.Duration;
import java.util.Objects;

final class LocalRateLimitBucket implements RateLimitBucket {
    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    private final long capacity;
    private final long refillPerSecond;

    private double availableTokens;
    private long lastRefillNanos;

    LocalRateLimitBucket(long capacity, long refillPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    @Override
    public synchronized boolean tryConsume(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }
        refill();
        if (availableTokens < tokens) {
            return false;
        }
        availableTokens -= tokens;
        return true;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double refillTokens = (elapsedNanos * (double) refillPerSecond) / NANOS_PER_SECOND;
        availableTokens = Math.min(capacity, availableTokens + refillTokens);
        lastRefillNanos = now;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalRateLimitBucket that)) {
            return false;
        }
        return capacity == that.capacity && refillPerSecond == that.refillPerSecond;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, refillPerSecond);
    }
}
