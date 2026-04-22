package com.fsss.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BytePatternScanner {
    private final List<byte[]> patterns;
    private final int maxPatternLen;
    private final byte[] tail;
    private int tailLen = 0;

    public BytePatternScanner(List<String> patterns) {
        List<byte[]> bytes = new ArrayList<>();
        int max = 0;
        for (String pattern : patterns) {
            byte[] p = pattern.getBytes(StandardCharsets.UTF_8);
            bytes.add(p);
            max = Math.max(max, p.length);
        }
        this.patterns = bytes;
        this.maxPatternLen = Math.max(1, max);
        this.tail = new byte[maxPatternLen - 1];
    }

    public String scan(byte[] buffer, int offset, int length) {
        byte[] combined = new byte[tailLen + length];
        System.arraycopy(tail, 0, combined, 0, tailLen);
        System.arraycopy(buffer, offset, combined, tailLen, length);

        for (byte[] pattern : patterns) {
            if (indexOf(combined, pattern) >= 0) {
                return new String(pattern, StandardCharsets.UTF_8);
            }
        }

        if (maxPatternLen > 1) {
            int newTailLen = Math.min(maxPatternLen - 1, combined.length);
            System.arraycopy(combined, combined.length - newTailLen, tail, 0, newTailLen);
            tailLen = newTailLen;
        }
        return null;
    }

    private int indexOf(byte[] data, byte[] pattern) {
        outer: for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
