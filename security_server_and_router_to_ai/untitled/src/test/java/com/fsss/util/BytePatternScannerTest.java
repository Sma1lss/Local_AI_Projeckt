package com.fsss.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BytePatternScannerTest {
    @Test
    void detectsPatternAcrossChunks() {
        BytePatternScanner scanner = new BytePatternScanner(List.of("evil"));
        byte[] part1 = "ev".getBytes(StandardCharsets.US_ASCII);
        byte[] part2 = "il".getBytes(StandardCharsets.US_ASCII);
        assertNull(scanner.scan(part1, 0, part1.length));
        assertEquals("evil", scanner.scan(part2, 0, part2.length));
    }
}
