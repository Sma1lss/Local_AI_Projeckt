package com.fsss.security;

import com.fsss.config.FsssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class RateLimiterService {
    private final FsssProperties properties;
    private final Map<String, BucketState> buckets = new ConcurrentHashMap<>();

    public RateLimitBucket resolveBucket(String key) {
        long now = System.currentTimeMillis();
        BucketState state = buckets.computeIfAbsent(key, ignored -> new BucketState(buildBucket(), new AtomicLong(now)));
        state.lastAccess().set(now);
        return state.bucket();
    }

    private RateLimitBucket buildBucket() {
        long capacity = properties.getSecurity().getRateLimit().getCapacity();
        long refill = properties.getSecurity().getRateLimit().getRefillPerSecond();
        return new LocalRateLimitBucket(capacity, refill);
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccess().get() < cutoff);
    }

    private record BucketState(RateLimitBucket bucket, AtomicLong lastAccess) {
    }
}
