package com.fsss.scanner;

import com.fsss.config.FsssProperties;
import com.fsss.domain.FileMetadata;
import com.fsss.domain.ScanFinding;
import com.fsss.scanner.impl.ClamAvScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClamAvScannerTest {
    private ServerSocket serverSocket;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    void returnsCleanWhenServerSaysOk() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverThread = new Thread(this::runFakeServer);
        serverThread.start();

        FsssProperties properties = FsssProperties.defaults()
                .withClamAv(new FsssProperties.ClamAvProperties(true, "127.0.0.1", port, Duration.ofSeconds(10)));

        ClamAvScanner scanner = new ClamAvScanner(properties);
        ScanHandle handle = scanner.start(new ScanContext(
                "scan",
                new FileMetadata("a", "a", "text/plain", 0, "", "", ""),
                java.time.Instant.now()
        ));
        handle.accept("hello".getBytes(), 0, 5);
        ScanFinding finding = handle.complete().block();

        assertNotNull(finding);
        assertEquals(ScannerOutcome.CLEAN, finding.outcome());
    }

    private void runFakeServer() {
        try (Socket socket = serverSocket.accept()) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            in.readNBytes("zINSTREAM\0".getBytes().length);
            while (true) {
                byte[] lenBytes = in.readNBytes(4);
                if (lenBytes.length < 4) {
                    break;
                }
                int len = ByteBuffer.wrap(lenBytes).getInt();
                if (len == 0) {
                    break;
                }
                in.readNBytes(len);
            }
            out.write("stream: OK\n".getBytes());
            out.flush();
        } catch (Exception ignored) {
        }
    }
}
