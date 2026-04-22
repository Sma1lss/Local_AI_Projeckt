package com.fsss.sandbox;

import com.fsss.domain.ScanVerdict;

public record DynamicSandboxResponse(
        ScanVerdict verdict,
        String details
) {
}
