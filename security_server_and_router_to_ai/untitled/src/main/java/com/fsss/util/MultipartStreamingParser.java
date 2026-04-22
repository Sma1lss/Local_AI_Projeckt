package com.fsss.util;

import com.fsss.config.FsssProperties;
import com.fsss.exception.MultipartParsingException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MultipartStreamingParser {
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=([^;]+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]*)\"");

    private final FsssProperties properties;

    public Mono<Void> parse(Flux<DataBuffer> body, String contentType, MultipartStreamConsumer consumer) {
        String boundary = extractBoundary(contentType);
        if (boundary == null || boundary.isBlank()) {
            return Mono.error(new MultipartParsingException("Missing multipart boundary"));
        }
        ParserState state = new ParserState(boundary, properties.getMaxHeaderBytes(), consumer);

        return Mono.create(sink -> body.subscribe(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                request(1);
            }

            @Override
            protected void hookOnNext(DataBuffer value) {
                try {
                    int readable = value.readableByteCount();
                    byte[] bytes = new byte[readable];
                    value.read(bytes);
                    state.process(bytes);
                    if (state.isDone()) {
                        sink.success();
                        cancel();
                        return;
                    }
                    request(1);
                } catch (Exception e) {
                    sink.error(e);
                    cancel();
                } finally {
                    DataBufferUtils.release(value);
                }
            }

            @Override
            protected void hookOnComplete() {
                try {
                    state.finish();
                    if (!state.isDone()) {
                        sink.error(new MultipartParsingException("Incomplete multipart stream"));
                        return;
                    }
                    sink.success();
                } catch (Exception e) {
                    sink.error(e);
                }
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                sink.error(throwable);
            }
        }));
    }

    private String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        String boundary = matcher.group(1).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
    }

    private static class ParserState {
        private enum State { FIND_START, READ_HEADERS, READ_CONTENT, DONE }

        private final MultipartStreamConsumer consumer;
        private final byte[] boundaryStart;
        private final byte[] boundaryMarker;
        private final int maxHeaderBytes;
        private final Deque<Byte> boundaryWindow;
        private final Deque<Byte> startWindow;
        private final ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        private final ByteArrayOutputStream emitBuffer = new ByteArrayOutputStream();
        private State state = State.FIND_START;
        private boolean done = false;

        private ParserState(String boundary, int maxHeaderBytes, MultipartStreamConsumer consumer) {
            this.consumer = consumer;
            this.boundaryStart = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
            this.boundaryMarker = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
            this.maxHeaderBytes = maxHeaderBytes;
            this.boundaryWindow = new ArrayDeque<>(boundaryMarker.length);
            this.startWindow = new ArrayDeque<>(boundaryStart.length);
        }

        void process(byte[] bytes) throws Exception {
            for (byte b : bytes) {
                switch (state) {
                    case FIND_START -> findStart(b);
                    case READ_HEADERS -> readHeaders(b);
                    case READ_CONTENT -> readContent(b);
                    case DONE -> done = true;
                }
                if (done) {
                    return;
                }
            }
        }

        void finish() throws Exception {
            if (state == State.READ_CONTENT) {
                flushEmitBuffer();
            }
        }

        boolean isDone() {
            return done || state == State.DONE;
        }

        private void findStart(byte b) {
            startWindow.add(b);
            if (startWindow.size() > boundaryStart.length) {
                startWindow.pollFirst();
            }
            if (matches(startWindow, boundaryStart)) {
                startWindow.clear();
                state = State.READ_HEADERS;
            }
        }

        private void readHeaders(byte b) throws Exception {
            headerBuffer.write(b);
            if (headerBuffer.size() > maxHeaderBytes) {
                throw new MultipartParsingException("Multipart headers too large");
            }
            byte[] data = headerBuffer.toByteArray();
            if (endsWithHeaderTerminator(data)) {
                String headersText = new String(data, 0, data.length - 4, StandardCharsets.US_ASCII);
                MultipartHeaders headers = parseHeaders(headersText);
                consumer.onHeaders(headers);
                headerBuffer.reset();
                state = State.READ_CONTENT;
            }
        }

        private void readContent(byte b) throws Exception {
            boundaryWindow.add(b);
            if (boundaryWindow.size() > boundaryMarker.length) {
                byte out = boundaryWindow.pollFirst();
                emitBuffer.write(out);
                if (emitBuffer.size() >= 16 * 1024) {
                    flushEmitBuffer();
                }
            }
            if (boundaryWindow.size() == boundaryMarker.length && matches(boundaryWindow, boundaryMarker)) {
                boundaryWindow.clear();
                flushEmitBuffer();
                consumer.onPartEnd();
                state = State.DONE;
                done = true;
            }
        }

        private void flushEmitBuffer() throws Exception {
            if (emitBuffer.size() > 0) {
                byte[] chunk = emitBuffer.toByteArray();
                consumer.onData(chunk, 0, chunk.length);
                emitBuffer.reset();
            }
        }

        private boolean matches(Deque<Byte> window, byte[] pattern) {
            if (window.size() != pattern.length) {
                return false;
            }
            int i = 0;
            for (byte b : window) {
                if (b != pattern[i++]) {
                    return false;
                }
            }
            return true;
        }

        private boolean endsWithHeaderTerminator(byte[] data) {
            int len = data.length;
            return len >= 4 && data[len - 4] == '\r' && data[len - 3] == '\n'
                    && data[len - 2] == '\r' && data[len - 1] == '\n';
        }

        private MultipartHeaders parseHeaders(String rawHeaders) {
            String[] lines = rawHeaders.split("\r\n");
            String name = null;
            String filename = null;
            String contentType = null;
            for (String line : lines) {
                int idx = line.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String headerName = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String headerValue = line.substring(idx + 1).trim();
                if (headerName.equals("content-disposition")) {
                    Matcher nameMatcher = NAME_PATTERN.matcher(headerValue);
                    if (nameMatcher.find()) {
                        name = nameMatcher.group(1);
                    }
                    Matcher fileMatcher = FILENAME_PATTERN.matcher(headerValue);
                    if (fileMatcher.find()) {
                        filename = fileMatcher.group(1);
                    }
                } else if (headerName.equals("content-type")) {
                    contentType = headerValue;
                }
            }
            return new MultipartHeaders(name, filename, contentType);
        }
    }
}
