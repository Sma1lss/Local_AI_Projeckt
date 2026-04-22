package com.fsss.util;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class SecureByteArrayOutputStream extends ByteArrayOutputStream {
    public SecureByteArrayOutputStream(int size) {
        super(size);
    }

    public byte[] buffer() {
        return buf;
    }

    public void wipe() {
        Arrays.fill(buf, 0, count, (byte) 0);
    }
}
