package com.fsss.security;

import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

public final class ApiKeySecret {
    private final byte[] encodedValue;

    private ApiKeySecret(byte[] encodedValue) {
        this.encodedValue = Arrays.copyOf(encodedValue, encodedValue.length);
    }

    public static ApiKeySecret from(String rawValue) {
        Objects.requireNonNull(rawValue, "rawValue");
        return new ApiKeySecret(rawValue.getBytes(StandardCharsets.UTF_8));
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(encodedValue, candidate.getBytes(StandardCharsets.UTF_8));
    }

    public void writeTo(HttpHeaders headers, String headerName) {
        headers.set(headerName, new String(encodedValue, StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "[PROTECTED]";
    }
}
