package com.fsss.scanner.impl;

import com.fsss.config.FsssProperties;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.ScanContext;
import com.fsss.scanner.ScanHandle;
import com.fsss.scanner.ScannerOutcome;
import com.fsss.scanner.StreamingScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Order(30)
@RequiredArgsConstructor
public class ClamAvScanner implements StreamingScanner {
    private static final int MAX_CHUNK = 1024 * 1024;
    private final FsssProperties properties;

    @Override
    public String name() {
        return "clamav";
    }

    @Override
    public ScanHandle start(ScanContext context) throws Exception {
        if (!properties.getClamav().isEnabled()) {
            return new NoopHandle();
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(properties.getClamav().getHost(), properties.getClamav().getPort()),
                (int) properties.getClamav().getTimeout().toMillis());
        socket.setSoTimeout((int) properties.getClamav().getTimeout().toMillis());

        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        InputStream in = new BufferedInputStream(socket.getInputStream());
        out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

        return new ScanHandle() {
            @Override
            public void accept(byte[] buffer, int offset, int length) throws IOException {
                int remaining = length;
                int pos = offset;
                while (remaining > 0) {
                    int chunk = Math.min(remaining, MAX_CHUNK);
                    out.write(ByteBuffer.allocate(4).putInt(chunk).array());
                    out.write(buffer, pos, chunk);
                    remaining -= chunk;
                    pos += chunk;
                }
            }

            @Override
            public Mono<ScanFinding> complete() {
                return Mono.fromCallable(() -> {
                    out.write(new byte[]{0, 0, 0, 0});
                    out.flush();
                    String response = readLine(in);
                    socket.close();
                    if (response == null) {
                        return new ScanFinding(name(), ScannerOutcome.ERROR, "ClamAV no response", Map.of());
                    }
                    if (response.contains("FOUND")) {
                        return new ScanFinding(name(), ScannerOutcome.MALICIOUS, "Malware detected", Map.of("clamav", response));
                    }
                    return new ScanFinding(name(), ScannerOutcome.CLEAN, "ClamAV OK", Map.of("clamav", response));
                });
            }

            @Override
            public void abort() {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        };
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static class NoopHandle implements ScanHandle {
        @Override
        public void accept(byte[] buffer, int offset, int length) {
        }

        @Override
        public Mono<ScanFinding> complete() {
            return Mono.just(new ScanFinding("clamav", ScannerOutcome.SKIPPED, "Disabled", Map.of()));
        }
    }
}
