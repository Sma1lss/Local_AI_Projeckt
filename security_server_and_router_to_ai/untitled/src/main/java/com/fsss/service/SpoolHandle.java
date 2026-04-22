package com.fsss.service;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public interface SpoolHandle extends AutoCloseable {
    OutputStream outputStream();

    void seal() throws IOException;

    InputStream openInputStream() throws IOException;

    Flux<DataBuffer> asFlux(DataBufferFactory factory, int bufferSize);

    long size();

    void secureDelete();

    default Path path() {
        return null;
    }

    @Override
    void close() throws Exception;
}
