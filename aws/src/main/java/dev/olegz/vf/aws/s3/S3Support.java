package dev.olegz.vf.aws.s3;

import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.ExternalConnectionException;
import dev.olegz.vf.common.util.CollectionOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * S3 object storage: create/get/delete objects. Transient failures are retried by the shared
 * {@link S3Client}'s standard retry strategy ({@link AwsClients}); this class only maps the
 * terminal outcome of each call (see {@link #execute}).
 * <p>
 * Thin static wrapper matching the module's other AWS helpers
 * ({@link dev.olegz.vf.aws.ecr.EcrSupport}, {@link S3Presigning}). The shared
 * {@link S3Client} is built lazily on first use and held for the JVM lifetime; it comes from
 * {@link AwsClients#s3Client}, so it is the real S3 client or the local one per
 * {@code vf.aws.local}, and is released by {@link #shutdown()} on context shutdown. The
 * underlying HTTP client is shared across AWS clients and owned by {@link AwsClients}.
 */
public final class S3Support {
    private S3Support() {
    }

    private static final Logger logger = LoggerFactory.getLogger(S3Support.class);

    /**
     * The shared {@link S3Client}, built lazily on first use and held for the JVM lifetime;
     * rebuilt if {@link #shutdown()} closed it (e.g. between test contexts). In local mode
     * {@link AwsClients#s3Client} ignores the HTTP client and returns a {@code LocalS3Client}.
     */
    private static final AwsClients.LazyClient<S3Client> CLIENT = AwsClients.lazyClient("S3", AwsClients::s3Client);

    private static S3Client client() {
        return CLIENT.get();
    }

    /** Idempotent: closes the shared client and its connection pool; safe to call more than once. */
    public static void shutdown() {
        CLIENT.shutdown();
    }

    private static String elapsed(long startTime) {
        return ", time=" + (System.currentTimeMillis() - startTime);
    }

    private static final int S3_404_NOT_FOUND_ERROR = 404;
    private static final int S3_416_RANGE_NOT_SATISFIABLE_ERROR = 416;

    /**
     * Runs a single S3 call and maps its terminal outcome to the module's contract. Transient
     * failures (throttling, 5xx, connection errors, per-attempt timeouts) are already retried by
     * the shared {@link S3Client}'s standard retry strategy ({@link AwsClients}), so this wrapper
     * does not loop. It only translates the final result: {@code NoSuchKey}/404/416 to {@code null}
     * when {@code ignoreNotFound}, the overall-timeout and connectivity failures (after retries are
     * exhausted) to {@link ExternalConnectionException}, and delegates every other S3/SDK error to
     * the module's shared translator {@link AwsExceptions#wrapAwsException} (S3 service errors thus
     * surface as {@code ExternalException}, like the other facades). Programming errors
     * (e.g. {@link NullPointerException}) propagate.
     */
    private static <T> T execute(Supplier<T> operation, boolean ignoreNotFound, Supplier<String> errorMessage) {
        final long startTime = System.currentTimeMillis();
        try {
            return operation.get();
        } catch (NoSuchKeyException e) {
            if (ignoreNotFound) {
                if (logger.isInfoEnabled()) {
                    logger.info("NoSuchKey " + errorMessage.get() + '\n' + e);
                }
                return null;
            }
            throw AwsExceptions.wrapAwsException(e, "S3 key not exists in " + errorMessage.get() + elapsed(startTime));
        } catch (S3Exception e) {
            int statusCode = e.statusCode();
            if (ignoreNotFound && ((S3_404_NOT_FOUND_ERROR == statusCode) || (S3_416_RANGE_NOT_SATISFIABLE_ERROR == statusCode))) {
                if (logger.isInfoEnabled()) {
                    logger.info("Not found " + errorMessage.get() + ", statusCode=" + statusCode + '\n' + e);
                }
                return null;
            }
            String message = "S3Exception in " + errorMessage.get() + ", statusCode=" + statusCode + elapsed(startTime);
            AwsExceptions.logAwsExceptionAsWarning(logger, e, message);
            throw AwsExceptions.wrapAwsException(e, message);
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException e) {
            String message = "Timeout in " + errorMessage.get() + elapsed(startTime);
            AwsExceptions.logAwsExceptionAsWarning(logger, e, message);
            throw new ExternalConnectionException(message);
        } catch (RejectedExecutionException e) {
            logger.warn("Execution rejected: " + errorMessage.get());
            return null;
        } catch (SdkClientException e) {
            String message = "Exception in " + errorMessage.get() + elapsed(startTime);
            AwsExceptions.logAwsExceptionAsWarning(logger, e, message);
            Throwable cause = e.getCause();
            if ((cause instanceof SocketTimeoutException) || (cause instanceof UnknownHostException)) {
                throw new ExternalConnectionException(message);
            }
            throw AwsExceptions.wrapAwsException(e, message);
        } catch (SdkException e) {
            String message = "Exception in " + errorMessage.get() + elapsed(startTime);
            AwsExceptions.logAwsExceptionAsWarning(logger, e, message);
            throw AwsExceptions.wrapAwsException(e, message);
        }
    }

    public static void createObject(String bucket, String objectId, byte[] data, String contentType, boolean encrypted) {
        if (logger.isDebugEnabled()) {
            logger.debug(">createObject() " + bucket + '/' + objectId +
                ", contentType=" + contentType + (encrypted ? ", encrypted" : ""));
        }

        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
            .bucket(bucket).key(objectId)
            .contentLength((long) data.length)
            // The base64-encoded 128-bit MD5 digest of the message (without the headers) according to RFC 1864.
            // This header can be used as a message integrity check to verify that the data is the same data that was originally sent.
            // Although it is optional, we recommend using the Content-MD5 mechanism as an end-to-end integrity check.
            .contentMD5(hashBase64(data, 0, data.length));
        // A standard MIME type describing the format of the contents.
        // For more information, see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.17.
        if (contentType != null) requestBuilder.contentType(contentType);
        // The server-side encryption algorithm used when storing this object in Amazon S3 (for example, AES256, aws:kms).
        if (encrypted) requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
        PutObjectRequest request = requestBuilder.build();

        S3Client client = client();
        execute(() -> client.putObject(request, requestBody(data, contentType)),
            false, () -> "put object " + bucket + '/' + objectId + ", size=" + data.length);
        if (logger.isDebugEnabled()) {
            logger.debug("<createObject() " + bucket + '/' + objectId);
        }
    }

    private static String hashBase64(byte[] input, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input, offset, length);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ApplicationFailureException(e);
        }
    }

    private static RequestBody requestBody(byte[] data, String contentType) {
        return RequestBody.fromContentProvider(
            () -> new ByteArrayInputStream(data), data.length, contentType != null ? contentType : "application/octet-stream");
    }


    public static byte[] getData(String bucket, String objectId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">getData() " + bucket + '/' + objectId);
        }

        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder().bucket(bucket).key(objectId);

        GetObjectRequest request = requestBuilder.build();
        S3Client c = client();
        byte[] data = execute(() -> c.getObjectAsBytes(request).asByteArray(),
            true, () -> "get " + bucket + '/' + objectId);
        if ((data == null) || (data.length == 0)) {
            logger.debug("<getData() not found {}/{}", bucket, objectId);
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("<getData() " + bucket + "/" + objectId + ", size=" + data.length);
        }
        return data;
    }

    /**************************
     *    Deletion
     **************************/

    private static final int MAX_DELETE_BATCH_SIZE = 1000;

    public static void deleteObjects(String bucket, List<String> objectIds, Collection<String> errorObjectIds) {
        int size = objectIds.size();
        if (logger.isDebugEnabled()) {
            logger.debug(">deleteObjects() bucket=" + bucket + ", size=" + size);
        }

        S3Client c = client();
        for (int from = 0; from < size; ) {
            int to = Math.min(from + MAX_DELETE_BATCH_SIZE, size);

            List<String> batch = objectIds.subList(from, to);
            try {
                List<S3Error> s3errors = deleteS3Objects(c, bucket, batch);
                if (s3errors != null) {
                    s3errors.forEach(s3error -> errorObjectIds.add(s3error.key()));
                }
            } catch (Exception e) {
                // execute() has already translated and logged AWS failures; this catch keeps one
                // bad batch from aborting the loop and records the keys that were not deleted.
                logger.error("Exception in deletion, bucket=" + bucket, e);
                errorObjectIds.addAll(batch);
            }
            from = to;
        }

        logger.debug("<deleteObjects()");
    }

    private static List<S3Error> deleteS3Objects(S3Client c, String bucket, Collection<String> objectIds) {
        List<ObjectIdentifier> objects = CollectionOps.map(objectIds, objectId -> ObjectIdentifier.builder().key(objectId).build());
        var request = DeleteObjectsRequest.builder().bucket(bucket).delete(d -> d.objects(objects).quiet(true)).build();
        return execute(() -> c.deleteObjects(request).errors(), false, () -> "delete objects, bucket=" + bucket);
    }

}
