package com.fsss.scanner;

public interface StreamingScanner {
    String name();

    ScanHandle start(ScanContext context) throws Exception;
}
