package dev.olegz.vf.common.io;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CircularByteBufferTest {

    @Test
    void reportsTotalBytesAndTruncation() {
        CircularByteBuffer buffer = new CircularByteBuffer(5, StandardCharsets.UTF_8);

        buffer.write("12345");
        assertEquals(5, buffer.totalBytesRead());
        assertFalse(buffer.isTruncated());

        buffer.write("67");
        assertEquals(7, buffer.totalBytesRead());
        assertTrue(buffer.isTruncated());
    }

    @Test
    void contentSpansMultipleInternalBuffers() {
        CircularByteBuffer buffer = new CircularByteBuffer(4097, StandardCharsets.UTF_8);
        String content = "x".repeat(4097);

        buffer.write(content);

        assertEquals(content, buffer.content());
    }

    @Test
    void contentWrapsAcrossInternalBufferBoundaries() {
        CircularByteBuffer buffer = new CircularByteBuffer(4097, StandardCharsets.UTF_8);

        buffer.write("a".repeat(4090) + "0123456789");

        assertEquals("a".repeat(4087) + "0123456789", buffer.content());
    }

    @Test
    void resetReusesBufferFromTheBeginning() {
        CircularByteBuffer buffer = new CircularByteBuffer(4097, StandardCharsets.UTF_8);
        buffer.write("x".repeat(3000));

        buffer.reset();
        buffer.write("new content");

        assertEquals("new content", buffer.content());
        assertEquals(11, buffer.totalBytesRead());
        assertFalse(buffer.isTruncated());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> new CircularByteBuffer(0, StandardCharsets.UTF_8));
    }

    @Test
    void contentSkipsTruncatedUtf8CharacterPrefix() {
        CircularByteBuffer buffer = new CircularByteBuffer(5, StandardCharsets.UTF_8);

        buffer.write("A€BCD");

        assertEquals("BCD", buffer.content());
    }

    @Test
    void contentPreservesCompleteUtf8CharacterAfterWrap() {
        CircularByteBuffer buffer = new CircularByteBuffer(5, StandardCharsets.UTF_8);

        buffer.write("A€CD");

        assertEquals("€CD", buffer.content());
    }

    @Test
    void contentSkipsPrefixContainingOnlyContinuationBytes() {
        CircularByteBuffer buffer = new CircularByteBuffer(2, StandardCharsets.UTF_8);

        buffer.write("€");

        assertEquals("", buffer.content());
    }
}
