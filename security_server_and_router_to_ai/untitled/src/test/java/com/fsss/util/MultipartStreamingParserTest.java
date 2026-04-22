package com.fsss.util;

import com.fsss.config.FsssProperties;
import com.fsss.exception.MultipartParsingException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartStreamingParserTest {

    @Test
    void parsesSinglePart() {
        MultipartStreamingParser parser = new MultipartStreamingParser(FsssProperties.defaults());
        String boundary = "boundary123";
        String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "hello world" +
                "\r\n--" + boundary + "--\r\n";
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(body.getBytes(StandardCharsets.US_ASCII));

        CapturingConsumer consumer = new CapturingConsumer();
        StepVerifier.create(parser.parse(Flux.just(buffer), "multipart/form-data; boundary=" + boundary, consumer))
                .verifyComplete();

        assertEquals("file", consumer.headers.name());
        assertEquals("test.txt", consumer.headers.filename());
        assertEquals("text/plain", consumer.headers.contentType());
        assertEquals("hello world", new String(consumer.bytes.toByteArray(), StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsMissingBoundary() {
        MultipartStreamingParser parser = new MultipartStreamingParser(FsssProperties.defaults());
        DataBuffer buffer = new DefaultDataBufferFactory().wrap("data".getBytes(StandardCharsets.US_ASCII));

        StepVerifier.create(parser.parse(Flux.just(buffer), "multipart/form-data", new CapturingConsumer()))
                .expectError(MultipartParsingException.class)
                .verify();
    }

    private static final class CapturingConsumer implements MultipartStreamConsumer {
        private MultipartHeaders headers;
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

        @Override
        public void onHeaders(MultipartHeaders headers) {
            this.headers = headers;
        }

        @Override
        public void onData(byte[] buffer, int offset, int length) {
            bytes.write(buffer, offset, length);
        }

        @Override
        public void onPartEnd() {
        }
    }
}
