package dev.olegz.vf.aws.kms;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;

/**
 * Envelope encryption over AWS KMS: a Key Encryption Key (KEK) is a KMS key that never leaves KMS,
 * and Data Encryption Keys (DEKs) are minted under it. The caller encrypts data with a plaintext
 * DEK, persists only the {@link DataKey#wrapped() wrapped} form, and unwraps it on demand.
 * <p>
 * Stateless static helper, matching the module's other thin AWS wrappers
 * ({@link dev.olegz.vf.aws.ecr.EcrSupport}, {@link dev.olegz.vf.aws.iam.IamSupport}).
 * The underlying client comes from {@link AwsClients#kmsClient()}, so it is the real KMS client or
 * the local one ({@link dev.olegz.vf.aws.local.LocalKmsClient}) per {@code vf.aws.local}.
 */
public final class KmsSupport {
    private KmsSupport() {
    }

    /**
     * A data encryption key in both forms: {@code plaintext} for immediate use (encrypt/decrypt
     * data, then wipe it) and {@code wrapped} (encrypted under the KEK) for persistence.
     */
    public record DataKey(byte[] plaintext, byte[] wrapped) {
    }

    // JVM-lifetime client; initialized lazily on first call when this class is loaded.
    private static final KmsClient CLIENT = AwsClients.kmsClient();

    /**
     * Provision a Key Encryption Key (a symmetric, encrypt/decrypt KMS key).
     *
     * @param description human-readable description for the key (may be {@code null})
     * @return the key id of the created KEK
     */
    public static String putKek(String description) {
        try {
            return CLIENT.createKey(
                r -> r.description(description).keyUsage(KeyUsageType.ENCRYPT_DECRYPT).keySpec(KeySpec.SYMMETRIC_DEFAULT)
            ).keyMetadata().keyId();
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while creating KMS key");
        }
    }

    /**
     * Generate a fresh AES-256 data encryption key under the given KEK.
     *
     * @param kekId the KEK key id (or ARN/alias)
     * @return the new key in plaintext and wrapped form
     */
    public static DataKey putDek(String kekId) {
        try {
            GenerateDataKeyResponse resp = CLIENT.generateDataKey(r -> r.keyId(kekId).keySpec(DataKeySpec.AES_256));
            return new DataKey(resp.plaintext().asByteArray(), resp.ciphertextBlob().asByteArray());
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while generating data key, kek=" + kekId);
        }
    }

    /**
     * Unwrap a DEK previously produced by {@link #putDek(String)}.
     *
     * @param kekId      the KEK key id the DEK was wrapped under
     * @param wrappedDek the wrapped DEK ({@link DataKey#wrapped()})
     * @return the plaintext DEK
     */
    public static byte[] getDek(String kekId, byte[] wrappedDek) {
        try {
            return CLIENT.decrypt(
                r -> r.keyId(kekId).ciphertextBlob(SdkBytes.fromByteArray(wrappedDek))
            ).plaintext().asByteArray();
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while decrypting data key, kek=" + kekId);
        }
    }
}
