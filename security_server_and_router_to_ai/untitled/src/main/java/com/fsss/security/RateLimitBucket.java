package com.fsss.security;

public interface RateLimitBucket {
    boolean tryConsume(long tokens);
}
