package com.fsss.scanner;

import com.fsss.domain.ScanFinding;
import reactor.core.publisher.Mono;

public interface ScanHandle {
    void accept(byte[] buffer, int offset, int length) throws Exception;

    Mono<ScanFinding> complete();

    default void abort() {
    }
}
