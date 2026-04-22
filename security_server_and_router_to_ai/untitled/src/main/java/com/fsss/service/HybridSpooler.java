package com.fsss.service;

import com.fsss.config.FsssProperties;
import com.fsss.util.SecureByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class HybridSpooler implements Spooler {
    private final FsssProperties properties;

    @Override
    public SpoolHandle create(String scanId) {
        return new HybridSpoolHandle(scanId, properties.getSpool());
    }

    private static class HybridSpoolHandle implements SpoolHandle {
        private final String scanId;
        private final FsssProperties.SpoolProperties props;
        private SecureByteArrayOutputStream memory;
        private OutputStream currentOut;
        private Path filePath;
        private long size;
        private boolean sealed = false;

        private HybridSpoolHandle(String scanId, FsssProperties.SpoolProperties props) {
            this.scanId = scanId;
            this.props = props;
            if (props.getMode() == FsssProperties.SpoolProperties.Mode.TMPFS) {
                switchToFile();
            } else {
                this.memory = new SecureByteArrayOutputStream((int) Math.min(1024 * 1024, props.getMemoryThresholdBytes()));
                this.currentOut = memory;
            }
        }

        @Override
        public OutputStream outputStream() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    write(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (sealed) {
                        throw new IOException("Spool is sealed");
                    }
                    ensureCapacity(len);
                    currentOut.write(b, off, len);
                    size += len;
                }

                @Override
                public void flush() throws IOException {
                    currentOut.flush();
                }

                @Override
                public void close() throws IOException {
                    currentOut.close();
                }
            };
        }

        @Override
        public void seal() throws IOException {
            if (!sealed) {
                currentOut.flush();
                sealed = true;
            }
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (!sealed) {
                seal();
            }
            if (filePath != null) {
                return new FileInputStream(filePath.toFile());
            }
            return new ByteArrayInputStream(memory.toByteArray());
        }

        @Override
        public Flux<DataBuffer> asFlux(DataBufferFactory factory, int bufferSize) {
            return DataBufferUtils.readInputStream(() -> {
                try {
                    return openInputStream();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, factory, bufferSize);
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void secureDelete() {
            try {
                if (filePath != null && Files.exists(filePath)) {
                    try (FileChannel channel = FileChannel.open(filePath, java.nio.file.StandardOpenOption.WRITE)) {
                        long remaining = channel.size();
                        ByteBuffer zeros = ByteBuffer.allocate(8192);
                        Arrays.fill(zeros.array(), (byte) 0);
                        while (remaining > 0) {
                            zeros.position(0);
                            int write = (int) Math.min(zeros.capacity(), remaining);
                            zeros.limit(write);
                            channel.write(zeros);
                            remaining -= write;
                        }
                    }
                    Files.deleteIfExists(filePath);
                }
                if (memory != null) {
                    memory.wipe();
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public Path path() {
            return filePath;
        }

        @Override
        public void close() throws Exception {
            if (currentOut != null) {
                currentOut.close();
            }
        }

        private void ensureCapacity(int len) throws IOException {
            if (props.getMode() == FsssProperties.SpoolProperties.Mode.MEMORY) {
                return;
            }
            if (filePath != null) {
                return;
            }
            if (size + len > props.getMemoryThresholdBytes()) {
                switchToFile();
            }
        }

        private void switchToFile() {
            try {
                Path dir = Path.of(props.getTempDir());
                Files.createDirectories(dir);
                filePath = Files.createTempFile(dir, scanId + "-", ".bin");
                FileOutputStream fos = new FileOutputStream(filePath.toFile());
                if (memory != null) {
                    memory.writeTo(fos);
                    memory.wipe();
                    memory = null;
                }
                currentOut = fos;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create spool file", e);
            }
        }
    }
}
