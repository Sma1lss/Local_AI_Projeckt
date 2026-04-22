package com.fsss.service;

import com.fsss.config.FsssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
public class MultipartRequestFactory {
    private final FsssProperties properties;
    private final DataBufferFactory dataBufferFactory;

    public MultiValueMap<String, org.springframework.http.HttpEntity<?>> createFileBody(SpoolHandle spoolHandle, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", spoolHandle.asFlux(dataBufferFactory, properties.getSpool().getBufferSizeBytes()), DataBuffer.class)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        return builder.build();
    }
}
