package com.fsss.domain;

import com.fsss.scanner.ScannerOutcome;

import java.util.Map;

public record ScanFinding(
        String scanner,
        ScannerOutcome outcome,
        String message,
        Map<String, Object> details
) {
}
