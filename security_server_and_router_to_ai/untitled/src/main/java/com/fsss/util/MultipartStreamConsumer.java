package com.fsss.util;

public interface MultipartStreamConsumer {
    void onHeaders(MultipartHeaders headers) throws Exception;

    void onData(byte[] buffer, int offset, int length) throws Exception;

    void onPartEnd() throws Exception;
}
