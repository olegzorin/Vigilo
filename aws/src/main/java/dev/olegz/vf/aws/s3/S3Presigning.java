package dev.olegz.vf.aws.s3;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.config.AwsCredentials;
import dev.olegz.vf.aws.local.LocalAws;
import dev.olegz.vf.aws.local.LocalS3HttpServer;
import dev.olegz.vf.common.ApplicationFailureException;
import software.amazon.awssdk.regions.ServiceMetadata;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.internal.checksums.ChecksumConstant;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Generates S3 pre-signed GET/PUT URLs. Holds a shared {@link S3Presigner} built from the
 * configured credentials and region. Stateless to callers, so it is safe to use from
 * non-Spring code (domain objects, REST POJOs).
 */
public final class S3Presigning {
    private S3Presigning() {
    }

    public static final Map<String, String> ENCRYPTION_HEADER =
        Map.of(ChecksumConstant.SERVER_SIDE_ENCRYPTION_HEADER, ServerSideEncryption.AES256.toString());

    // Built lazily so that local mode (which has no AWS credentials/region) never triggers it.
    private static volatile S3Presigner presigner;

    private static S3Presigner presigner() {
        S3Presigner p = presigner;
        if (p == null) {
            synchronized (S3Presigning.class) {
                p = presigner;
                if (p == null) {
                    URI endpoint = URI.create("https://" + ServiceMetadata.of(S3Client.SERVICE_NAME).endpointFor(AwsClients.REGION));
                    presigner = p = S3Presigner.builder()
                        .credentialsProvider(AwsCredentials.getProvider())
                        .region(AwsClients.REGION)
                        .endpointOverride(endpoint)
                        .build();
                }
            }
        }
        return p;
    }

    /**
     * In local mode the "presigned" URL is an {@code http://} URL served by {@link LocalS3HttpServer}
     * over the backing object store. Unlike the Phase 1 {@code file://} stopgap this is reachable
     * from inside a build container (via {@code host.docker.internal}), which is what
     * {@code BuildConfig.makeDockerfile} requires.
     */
    private static URL localHttpUrl(String bucket, String objectId) {
        try {
            return URI.create(LocalS3HttpServer.urlFor(bucket, objectId)).toURL();
        } catch (MalformedURLException e) {
            throw new ApplicationFailureException("Cannot build local S3 URL for " + bucket + '/' + objectId, e);
        }
    }

    public static URL makeS3WritePresignedUrl(String bucket, String objectId, long expiration, String contentType, boolean encrypted) {
        if (LocalAws.ENABLED) return localHttpUrl(bucket, objectId);

        ServerSideEncryption enc = encrypted ? ServerSideEncryption.AES256 : null;
        return presigner().presignPutObject(r -> r.signatureDuration(Duration.ofMillis(expiration))
            .putObjectRequest(o -> o.bucket(bucket).key(objectId).contentType(contentType).serverSideEncryption(enc))
        ).url();
    }

    public static URL makeS3ReadPresignedUrl(String bucket, String objectId, long expiration, String contentDisposition) {
        if (LocalAws.ENABLED) return localHttpUrl(bucket, objectId);

        return presigner().presignGetObject(r -> r.signatureDuration(Duration.ofMillis(expiration))
            .getObjectRequest(o -> o.bucket(bucket).key(objectId).responseContentDisposition(contentDisposition))
        ).url();
    }
}
