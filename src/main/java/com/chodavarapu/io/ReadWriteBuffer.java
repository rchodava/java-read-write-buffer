package com.chodavarapu.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class ReadWriteBuffer {
    private static final long MAXIMUM_WAIT = 5000;

    private static byte[] ensureBufferOfCapacity(byte[] buffer, int minCapacity) {
        if (minCapacity - buffer.length > 0) {
            return grow(buffer, minCapacity);
        }

        return buffer;
    }

    private static byte[] grow(byte[] buffer, int minCapacity) {
        int currentCapacity = buffer.length;
        int newCapacity = currentCapacity << 1;
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }

        if (newCapacity < 0) {
            throw new OutOfMemoryError();
        }

        return Arrays.copyOf(buffer, newCapacity);
    }

    private final Object availabilityBarrier = new Object();
    private byte[] buffer;
    private final Object bufferMonitor = new Object();
    private final Object openCountMonitor = new Object();
    private int openOutputStreams = 0;
    private int writtenPosition = 0;

    public ReadWriteBuffer(byte[] initialBuffer) {
        if (initialBuffer != null) {
            buffer = initialBuffer;
            writtenPosition = buffer.length;
        } else {
            buffer = new byte[1024];
        }
    }

    public ReadWriteBuffer() {
        this(null);
    }

    public InputStream openInputStream() {
        return new BufferInputStream();
    }

    public OutputStream openOutputStream() {
        return new BufferOutputStream();
    }

    private class BufferInputStream extends InputStream {
        private int readPosition = 0;
        private long waitStart;

        private void waitForWriters() throws InterruptedException, IOException {
            if (System.currentTimeMillis() - waitStart > MAXIMUM_WAIT) {
                throw new BlockingReadTimeoutException("No bytes available for reading for over 5 seconds, quitting!");
            }

            synchronized (availabilityBarrier) {
                availabilityBarrier.wait(1000);
            }
        }

        private int numberOfAvailableBytes() throws IOException {
            int numberOfAvailableBytes;

            synchronized (bufferMonitor) {
                numberOfAvailableBytes = writtenPosition - readPosition;
            }

            try {
                if (numberOfAvailableBytes < 1) {
                    waitStart = System.currentTimeMillis();
                    do {
                        synchronized (openCountMonitor) {
                            if (openOutputStreams == 0) {
                                return -1;
                            }
                        }

                        waitForWriters();

                        synchronized (bufferMonitor) {
                            numberOfAvailableBytes = writtenPosition - readPosition;
                        }
                    } while (numberOfAvailableBytes < 1);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            return numberOfAvailableBytes;
        }

        @Override
        public int read() throws IOException {
            if (numberOfAvailableBytes() < 0) {
                return -1;
            }

            byte byteRead;
            synchronized (bufferMonitor) {
                byteRead = buffer[readPosition];
            }

            readPosition++;

            return byteRead;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            int availableBytes = numberOfAvailableBytes();
            if (availableBytes < 0) {
                return -1;
            }

            int numberOfBytesRead = Math.min(availableBytes, len);

            synchronized (bufferMonitor) {
                System.arraycopy(buffer, readPosition, b, off, numberOfBytesRead);
            }

            readPosition += numberOfBytesRead;
            return numberOfBytesRead;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }

            long numberOfBytesSkipped;
            synchronized (bufferMonitor) {
                numberOfBytesSkipped = Math.min(writtenPosition - readPosition, n);
            }
            readPosition += numberOfBytesSkipped;

            return numberOfBytesSkipped;
        }

        @Override
        public int available() throws IOException {
            synchronized (bufferMonitor) {
                return writtenPosition - readPosition;
            }
        }

        @Override
        public void close() throws IOException {
        }
    }

    private class BufferOutputStream extends OutputStream {
        public BufferOutputStream() {
            synchronized (openCountMonitor) {
                openOutputStreams++;
            }

        }

        private void releaseReaders() {
            synchronized (availabilityBarrier) {
                availabilityBarrier.notifyAll();
            }
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (bufferMonitor) {
                buffer = ensureBufferOfCapacity(buffer, writtenPosition + 1);
                buffer[writtenPosition] = (byte) b;
                writtenPosition++;
            }

            releaseReaders();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            synchronized (bufferMonitor) {
                buffer = ensureBufferOfCapacity(buffer, writtenPosition + len);
                System.arraycopy(b, off, buffer, writtenPosition, len);
                writtenPosition +=  len;
            }

            releaseReaders();
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
            synchronized (openCountMonitor) {
                openOutputStreams--;
            }

            releaseReaders();
        }
    }
}
