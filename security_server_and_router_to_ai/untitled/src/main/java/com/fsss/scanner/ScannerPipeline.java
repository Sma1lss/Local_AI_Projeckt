package com.fsss.scanner;

import com.fsss.domain.ScanFinding;
import com.fsss.util.DetailsMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScannerPipeline {
    private final List<StreamingScanner> scanners;

    public ScanSession startSession(ScanContext context) {
        List<ScanHandle> handles = new ArrayList<>();
        for (StreamingScanner scanner : scanners) {
            try {
                handles.add(scanner.start(context));
            } catch (Exception ex) {
                handles.add(new ScanHandle() {
                    @Override
                    public void accept(byte[] buffer, int offset, int length) {
                    }

                    @Override
                    public Mono<ScanFinding> complete() {
                        return Mono.just(new ScanFinding(
                                scanner.name(),
                                ScannerOutcome.ERROR,
                                "Scanner init failed",
                                DetailsMap.create().add("error", ex.getMessage()).build()
                        ));
                    }
                });
            }
        }
        return new ScanSession(handles);
    }

    public static class ScanSession {
        private final List<ScanHandle> handles;

        private ScanSession(List<ScanHandle> handles) {
            this.handles = handles;
        }

        public void accept(byte[] buffer, int offset, int length) throws Exception {
            for (ScanHandle handle : handles) {
                handle.accept(buffer, offset, length);
            }
        }

        public Mono<List<ScanFinding>> complete() {
            return Flux.fromIterable(handles)
                    .flatMap(ScanHandle::complete)
                    .collectList();
        }

        public void abort() {
            for (ScanHandle handle : handles) {
                handle.abort();
            }
        }
    }
}
