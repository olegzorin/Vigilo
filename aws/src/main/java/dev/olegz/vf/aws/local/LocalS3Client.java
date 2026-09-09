package dev.olegz.vf.aws.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Local file-system implementation of {@link S3Client}. Objects are stored as files under
 * {@code <root>/s3/<bucket>/<key>}. Only the operations used by
 * {@code dev.olegz.vf.aws.s3.S3Support} are implemented; every other operation
 * inherits the SDK interface default, which throws {@link UnsupportedOperationException}.
 */
public final class LocalS3Client implements S3Client {

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        File file = LocalAws.s3File(request.bucket(), request.key());
        File parent = file.getParentFile();
        if ((parent != null) && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new UncheckedIOException(new IOException("Cannot create directory " + parent));
        }
        try (InputStream in = body.contentStreamProvider().newStream()) {
            Files.write(file.toPath(), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write S3 object " + request.bucket() + '/' + request.key(), e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
        File file = LocalAws.s3File(request.bucket(), request.key());
        if (!file.isFile()) {
            throw NoSuchKeyException.builder()
                .message("No such key " + request.bucket() + '/' + request.key()).build();
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read S3 object " + request.bucket() + '/' + request.key(), e);
        }
        data = applyRange(data, request.range());
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), data);
    }

    /** Apply an HTTP "bytes=begin-end" / "bytes=begin-" range header to the data, if present. */
    private static byte[] applyRange(byte[] data, String range) {
        if (range == null || range.isBlank()) return data;

        String spec = range.startsWith("bytes=") ? range.substring("bytes=".length()) : range;
        int dash = spec.indexOf('-');
        if (dash < 0) return data;

        try {
            int begin = dash == 0 ? 0 : Integer.parseInt(spec.substring(0, dash).trim());
            String endStr = spec.substring(dash + 1).trim();
            int endInclusive = endStr.isEmpty() ? data.length - 1 : Integer.parseInt(endStr);
            if (begin < 0) begin = 0;
            if (endInclusive >= data.length) endInclusive = data.length - 1;
            if (begin > endInclusive) return new byte[0];
            return Arrays.copyOfRange(data, begin, endInclusive + 1);
        } catch (NumberFormatException e) {
            return data;
        }
    }

    @Override
    public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest request) {
        for (ObjectIdentifier object : request.delete().objects()) {
            File file = LocalAws.s3File(request.bucket(), object.key());
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return DeleteObjectsResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return S3Client.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
