package com.fsss.util;

public class EntropyCalculator {
    private final long[] counts = new long[256];
    private long total = 0;

    public void update(byte[] buffer, int offset, int length) {
        for (int i = 0; i < length; i++) {
            counts[buffer[offset + i] & 0xFF]++;
        }
        total += length;
    }

    public double entropy() {
        if (total == 0) {
            return 0.0;
        }
        double entropy = 0.0;
        for (long count : counts) {
            if (count == 0) continue;
            double p = (double) count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }
}
