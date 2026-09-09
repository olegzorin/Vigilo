package dev.olegz.vf.aws.local;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.util.AesEncryptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

/**
 * Local implementation of {@link KmsClient} that performs envelope encryption with a Key Encryption
 * Key supplied as a base64-encoded AES key in the {@code VF-KEK} system property, instead of
 * calling KMS. DEKs are wrapped with {@code AES/GCM/NoPadding} using the same layout as
 * {@link dev.olegz.vf.common.util.AesEncryptor} (a random 12-byte IV prefixed to the
 * ciphertext+tag), so a ciphertext blob is {@code IV || GCM(plaintext)}.
 * <p>
 * Only the methods used by {@link dev.olegz.vf.aws.kms.KmsSupport} are implemented:
 * {@code createKey} (validates the KEK is configured and hands back a synthetic key id),
 * {@code generateDataKey}, and {@code decrypt}. The {@code keyId} on a request is accepted but
 * ignored - all wrapping uses the single property-supplied KEK.
 */
public final class LocalKmsClient implements KmsClient {
    private static final SecureRandom secureRandom = new SecureRandom();

    /** System property holding the base64-encoded AES KEK. */
    static final String KEK_PROPERTY = "VF-KEK";

    private static final int DEFAULT_DEK_BYTES = 32; // AES-256

    private static AesEncryptor loadKek() {
        String encoded = System.getProperty(KEK_PROPERTY);
        if (encoded == null || encoded.isBlank()) {
            throw new ApplicationFailureException("Local KMS KEK is not configured; set system property '" + KEK_PROPERTY + "'");
        }
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key == null || key.length == 0) {
            throw new ApplicationFailureException("System property '" + KEK_PROPERTY + "' is not valid base64");
        }
        return new AesEncryptor(key);
    }

    @Override
    public CreateKeyResponse createKey(CreateKeyRequest request) {
        loadKek(); // fail fast if the local KEK is missing
        String keyId = UUID.randomUUID().toString();
        KeyMetadata metadata = KeyMetadata.builder()
            .keyId(keyId)
            .arn("arn:aws:kms:local:" + LocalAws.ACCOUNT_ID + ":key/" + keyId)
            .description(request.description())
            .keyUsage(request.keyUsage())
            .keySpec(request.keySpec())
            .build();
        return CreateKeyResponse.builder().keyMetadata(metadata).build();
    }

    @Override
    public GenerateDataKeyResponse generateDataKey(GenerateDataKeyRequest request) {
        AesEncryptor kek = loadKek();
        byte[] plaintext = new byte[dekLength(request)];
        secureRandom.nextBytes(plaintext);
        byte[] wrapped = wrap(kek, plaintext);
        return GenerateDataKeyResponse.builder()
            .keyId(request.keyId())
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .ciphertextBlob(SdkBytes.fromByteArray(wrapped))
            .build();
    }

    @Override
    public DecryptResponse decrypt(DecryptRequest request) {
        AesEncryptor kek = loadKek();
        byte[] plaintext = unwrap(kek, request.ciphertextBlob().asByteArray());
        return DecryptResponse.builder()
            .keyId(request.keyId())
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .build();
    }

    private static int dekLength(GenerateDataKeyRequest request) {
        if (request.numberOfBytes() != null) return request.numberOfBytes();
        return request.keySpec() == DataKeySpec.AES_128 ? 16 : DEFAULT_DEK_BYTES;
    }

    private static byte[] wrap(AesEncryptor kek, byte[] plaintext) {
        try {
            return kek.encrypt(plaintext);
        } catch (GeneralSecurityException e) {
            throw new ApplicationFailureException("Failed to wrap data key with local KEK", e);
        }
    }

    private static byte[] unwrap(AesEncryptor kek, byte[] blob) {
        try {
            return kek.decrypt(blob);
        } catch (GeneralSecurityException e) {
            throw new ApplicationFailureException("Failed to unwrap data key with local KEK", e);
        }
    }

    @Override
    public String serviceName() {
        return KmsClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
