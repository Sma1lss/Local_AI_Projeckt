package com.fsss.logging;

import com.fsss.config.FsssProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SecurityEventLogger {
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");
    private final FsssProperties properties;
    private final WebClient webClient;

    public void threatDetected(Map<String, Object> payload) {
        SECURITY_LOG.warn("Threat detected: {}", payload);
        sendWebhook("threat", payload);
    }

    public void suspiciousActivity(Map<String, Object> payload) {
        SECURITY_LOG.warn("Suspicious activity: {}", payload);
        sendWebhook("suspicious", payload);
    }

    public void rateLimitExceeded(String ip, String path) {
        Map<String, Object> payload = basePayload();
        payload.put("ip", ip);
        payload.put("path", path);
        payload.put("event", "rate_limit");
        SECURITY_LOG.warn("Rate limit exceeded: {}", payload);
        sendWebhook("rate_limit", payload);
    }

    public void tooManyConcurrentUploads(String path) {
        Map<String, Object> payload = basePayload();
        payload.put("path", path);
        payload.put("event", "in_flight_limit");
        SECURITY_LOG.warn("Too many concurrent uploads: {}", payload);
        sendWebhook("in_flight_limit", payload);
    }

    private void sendWebhook(String type, Map<String, Object> payload) {
        String url = properties.getLogging().getSecurityWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, Object> body = new HashMap<>(payload);
        body.put("type", type);
        body.put("ts", Instant.now().toString());
        webClient.post().uri(url).bodyValue(body).retrieve().bodyToMono(Void.class).onErrorResume(ex -> Mono.empty()).subscribe();
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ts", Instant.now().toString());
        return payload;
    }
}
