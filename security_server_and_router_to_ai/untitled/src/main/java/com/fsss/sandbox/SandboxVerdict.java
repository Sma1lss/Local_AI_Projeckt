package com.fsss.sandbox;

import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;

import java.util.List;

public record SandboxVerdict(
        ScanVerdict verdict,
        List<ScanFinding> findings
) {
}
