package dev.olegz.vf.core.codec;

import java.util.Arrays;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.lz4.LZ4SafeDecompressor;

/**
 * Utility class providing LZ4 compression and decompression operations.
 *
 * This class offers methods for compressing and decompressing byte arrays using the LZ4 algorithm.
 * It supports both basic compression operations and length-prefixed compression where the original
 * data length is embedded in the compressed output for faster decompression.
 *
 * The class uses the LZ4Factory to obtain the fastest available compressor and decompressor
 * implementations for the current platform. All methods are static and the class is designed
 * to be used as a utility without instantiation.
 *
 * Thread-safety: This class is thread-safe as it uses thread-safe LZ4Factory instances
 * and does not maintain any mutable state.
 */
public class Lz4Codec {
    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
    private static final byte LENGTH_HEAD = 'L';

    /**
     * Compresses the provided byte array using LZ4 fast compression algorithm.
     * This method uses the fast compressor variant which prioritizes compression speed
     * over compression ratio.
     *
     * @param source the byte array to be compressed
     * @return a byte array containing the LZ4 compressed data
     */
    public static byte[] lz4Compress(byte[] source) {
        LZ4Compressor compressor = lz4Factory.fastCompressor();
        return compressor.compress(source);
    }

    /**
     * Decompresses the provided LZ4 compressed byte array using the fast decompression algorithm.
     * This method requires the original uncompressed data length to be known in advance.
     * The fast decompressor variant provides faster decompression speed but requires the exact
     * original length for proper decompression.
     *
     * @param compressed the LZ4 compressed byte array to be decompressed
     * @param originalLength the exact length of the original uncompressed data
     * @return a byte array containing the decompressed data with the specified original length
     */
    public static byte[] lz4FastUncompress(byte[] compressed, int originalLength) {
        LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();
        return decompressor.decompress(compressed, 0, originalLength);
    }

    private static byte[] lz4SafeUncompress(byte[] compressed, int maxDestLen) {
        LZ4SafeDecompressor decompressor = lz4Factory.safeDecompressor();
        return decompressor.decompress(compressed, maxDestLen);
    }

    /**
     * Compresses the provided byte array using LZ4 fast compression algorithm with embedded length metadata.
     * This method prepends an 8-byte header to the compressed data containing the original uncompressed length.
     * The header format consists of 4 marker bytes (LENGTH_HEAD) surrounding the 4-byte little-endian
     * representation of the source length, allowing for self-describing compressed data that can be
     * decompressed without external knowledge of the original size.
     *
     * @param source the byte array to be compressed
     * @return a byte array containing an 8-byte length header followed by the LZ4 compressed data
     */
    public static byte[] lz4CompressWithLength(byte[] source) {
        LZ4Compressor compressor = lz4Factory.fastCompressor();

        int maxCompressedLength = compressor.maxCompressedLength(source.length);
        byte[] compressed = new byte[maxCompressedLength + 8];

        // wrapped source length (4 bytes, little-endian)
        compressed[0] = LENGTH_HEAD;
        compressed[1] = LENGTH_HEAD;
        compressed[2] = (byte) (source.length & 0xFF);         // Least significant byte
        compressed[3] = (byte) ((source.length >> 8) & 0xFF);
        compressed[4] = (byte) ((source.length >> 16) & 0xFF);
        compressed[5] = (byte) ((source.length >> 24) & 0xFF); // Most significant byte
        compressed[6] = LENGTH_HEAD;
        compressed[7] = LENGTH_HEAD;

        int compressedLength = compressor.compress(source, 0, source.length, compressed, 8, maxCompressedLength);
        return Arrays.copyOf(compressed, compressedLength + 8);
    }

    /**
     * Decompresses an LZ4 compressed byte array that may contain embedded length metadata.
     * This method first attempts to read an 8-byte header containing the original uncompressed length.
     * If a valid header is present (identified by LENGTH_HEAD markers), it uses fast decompression
     * with the extracted length. If the header is invalid, missing, or the extracted length exceeds
     * the maximum allowed destination length, it falls back to safe decompression mode which does
     * not require prior knowledge of the original length.
     *
     * @param compressed the LZ4 compressed byte array, optionally containing an 8-byte length header
     * @param maxDestLen the maximum allowed length of the decompressed output, used for validation and safe decompression fallback
     * @return a byte array containing the decompressed data
     */
    public static byte[] lz4UncompressWithLength(byte[] compressed, int maxDestLen) {
        int originalLength;
        if ((compressed.length < 9) ||
            (compressed[0] != LENGTH_HEAD) || (compressed[1] != LENGTH_HEAD) || (compressed[6] != LENGTH_HEAD) || (compressed[7] != LENGTH_HEAD) ||
            ((originalLength = (compressed[2] & 0xFF) | ((compressed[3] & 0xFF) << 8) | ((compressed[4] & 0xFF) << 16) | ((compressed[5] & 0xFF) << 24)) <= 0) ||
            (originalLength > maxDestLen))
        {
            return lz4SafeUncompress(compressed, maxDestLen);
        }

        LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();
        return decompressor.decompress(compressed, 8, originalLength);
    }
}
