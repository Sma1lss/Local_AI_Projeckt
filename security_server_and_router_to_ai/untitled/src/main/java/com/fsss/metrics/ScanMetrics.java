package com.fsss.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ScanMetrics {
    private final Counter cleanCounter;
    private final Counter suspiciousCounter;
    private final Counter maliciousCounter;
    private final Counter errorCounter;
    private final Timer scanTimer;

    public ScanMetrics(MeterRegistry registry) {
        this.cleanCounter = registry.counter("fsss.scan.clean");
        this.suspiciousCounter = registry.counter("fsss.scan.suspicious");
        this.maliciousCounter = registry.counter("fsss.scan.malicious");
        this.errorCounter = registry.counter("fsss.scan.error");
        this.scanTimer = registry.timer("fsss.scan.duration");
    }

    public void record(String verdict, Duration duration) {
        switch (verdict) {
            case "CLEAN" -> cleanCounter.increment();
            case "SUSPICIOUS" -> suspiciousCounter.increment();
            case "MALICIOUS" -> maliciousCounter.increment();
            default -> errorCounter.increment();
        }
        if (duration != null) {
            scanTimer.record(duration);
        }
    }
}
