package dev.olegz.vf.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The size parameter determines the maximum capacity of the buffer.
 * When the limit is reached, new data is written to the tail of the buffer
 * and the header of the buffer is removed.
 * This behavior can be useful for large log entries, where the most recent
 * log events are most useful in identifying the cause of errors.
 */
final class CircularByteBuffer {

    private static final int CHUNK_SIZE = 2048;

    private final Charset charset;
    private final int capacity;
    private final byte[][] buffers;
    private int pos;
    private boolean filled;
    private long totalBytesRead;

    public CircularByteBuffer(int size, Charset charset) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive: " + size);
        }
        this.charset = charset;
        this.capacity = size;
        this.buffers = new byte[1 + (size - 1) / CHUNK_SIZE][];
    }

    synchronized void write(byte[] b, int count) {
        if (count < 0 || count > b.length) {
            throw new IndexOutOfBoundsException("count=" + count + ", length=" + b.length);
        }
        totalBytesRead += count;
        for (int tail = count; tail > 0;) {
            int bufferIndex = pos / CHUNK_SIZE;
            int bufferPos = pos % CHUNK_SIZE;
            byte[] buffer = buffer(bufferIndex);
            int n = Math.min(tail, buffer.length - bufferPos);
            System.arraycopy(b, count - tail, buffer, bufferPos, n);
            tail -= n;
            pos += n;

            if (pos == capacity) {
                pos = 0;
                filled = true;
            }
        }
    }

    public void write(String str) {
        byte[] bytes = str.getBytes(charset);
        write(bytes, bytes.length);
    }

    public void feedFrom(InputStream in) throws IOException {
        byte[] buf = new byte[1024];
        for (int read = 0; read >= 0; ) {
            try {
                read = in.read(buf);
            } catch (IOException e) {
                if ("Stream closed".equals(e.getMessage())) return;
                throw e;
            }
            if (read > 0) {
                write(buf, read);
            }
        }
    }

    public void reset() {
        synchronized (this) {
            pos = 0;
            filled = false;
            totalBytesRead = 0;
        }
    }

    public synchronized long totalBytesRead() {
        return totalBytesRead;
    }

    public synchronized boolean isTruncated() {
        return totalBytesRead > capacity;
    }

    public String content() {
        byte[] b;
        int len;
        boolean wrapped;
        synchronized (this) {
            wrapped = filled;
            len = filled ? capacity : pos;
            b = new byte[len];
            int start = filled ? pos : 0;
            copyTo(start, b, 0, len);
        }
        int offset = wrapped && StandardCharsets.UTF_8.equals(charset) ? utf8StartOffset(b, len) : 0;
        return new String(b, offset, len - offset, charset);
    }

    private byte[] buffer(int index) {
        byte[] buffer = buffers[index];
        if (buffer == null) {
            int size = Math.min(CHUNK_SIZE, capacity - index * CHUNK_SIZE);
            buffer = new byte[size];
            buffers[index] = buffer;
        }
        return buffer;
    }

    private void copyTo(int sourcePos, byte[] target, int targetPos, int count) {
        for (int tail = count; tail > 0;) {
            int bufferIndex = sourcePos / CHUNK_SIZE;
            int bufferPos = sourcePos % CHUNK_SIZE;
            byte[] buffer = buffers[bufferIndex];
            int n = Math.min(tail, buffer.length - bufferPos);
            System.arraycopy(buffer, bufferPos, target, targetPos, n);
            tail -= n;
            targetPos += n;
            sourcePos += n;
            if (sourcePos == capacity) {
                sourcePos = 0;
            }
        }
    }

    private static int utf8StartOffset(byte[] bytes, int length) {
        int offset = 0;
        while (offset < length && (bytes[offset] & 0xC0) == 0x80) {
            offset++;
        }
        return offset;
    }

}
