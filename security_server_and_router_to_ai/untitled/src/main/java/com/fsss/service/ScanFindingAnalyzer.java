package com.fsss.service;

import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;
import com.fsss.scanner.ScannerOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScanFindingAnalyzer {
    public ScanVerdict resolveVerdict(List<ScanFinding> findings) {
        if (containsOutcome(findings, ScannerOutcome.MALICIOUS)) {
            return ScanVerdict.MALICIOUS;
        }
        if (containsOutcome(findings, ScannerOutcome.ERROR)) {
            return ScanVerdict.ERROR;
        }
        if (containsOutcome(findings, ScannerOutcome.SUSPICIOUS)) {
            return ScanVerdict.SUSPICIOUS;
        }
        return ScanVerdict.CLEAN;
    }

    public String resolveDetectedMime(List<ScanFinding> findings) {
        return findings.stream()
                .filter(finding -> "tika-mime".equals(finding.scanner()))
                .map(finding -> finding.details().get("detected"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    public List<ScanFinding> merge(List<ScanFinding> baseFindings, List<ScanFinding> additionalFindings) {
        List<ScanFinding> merged = new ArrayList<>(baseFindings);
        merged.addAll(additionalFindings);
        return List.copyOf(merged);
    }

    private boolean containsOutcome(List<ScanFinding> findings, ScannerOutcome outcome) {
        return findings.stream().anyMatch(finding -> finding.outcome() == outcome);
    }
}
