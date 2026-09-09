package dev.olegz.vf.aws.config;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.PropertyStore;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * AWS credentials bootstrap. Lazily reads and decrypts the configured access/secret keys once,
 * then exposes a shared {@link AwsCredentialsProvider}. Resolution is deferred until first use so
 * that local mode (which has no AWS credentials) never triggers it.
 */
public final class AwsCredentials {
    private AwsCredentials() {
    }

    private static volatile AwsCredentialsProvider provider;
    private static final Object lock = new Object();

    public static AwsCredentialsProvider getProvider() {
        if (provider != null) return provider;
        synchronized (lock) {
            if (provider == null) {
                String accessKeyId = PropertyStore.decrypt("vf.aws.accessKeyId");
                String secretAccessKey = PropertyStore.decrypt("vf.aws.secretAccessKey");
                if (accessKeyId == null || accessKeyId.isBlank()
                    || secretAccessKey == null || secretAccessKey.isBlank()) {
                    throw new ApplicationFailureException("AWS credentials are not configured");
                }
                provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
            }
            return provider;
        }
    }
}
