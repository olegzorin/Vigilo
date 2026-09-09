package dev.olegz.vf.aws.local;

import java.util.Base64;

import dev.olegz.vf.aws.LocalAwsTestSupport;
import dev.olegz.vf.common.ApplicationFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalKmsClient}: the KEK comes from the {@code VF-KEK} system property,
 * set to a fixed base64 AES-256 key for the duration of each test.
 */
class LocalKmsClientTest extends LocalAwsTestSupport {

    private String previousKek;

    @BeforeEach
    void setKek() {
        previousKek = System.getProperty(LocalKmsClient.KEK_PROPERTY);
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        System.setProperty(LocalKmsClient.KEK_PROPERTY, Base64.getEncoder().encodeToString(key));
    }

    @AfterEach
    void restoreKek() {
        if (previousKek == null) System.clearProperty(LocalKmsClient.KEK_PROPERTY);
        else System.setProperty(LocalKmsClient.KEK_PROPERTY, previousKek);
    }

    @Test
    void createKeyReturnsSyntheticKeyId() {
        String keyId = new LocalKmsClient().createKey(r -> r.description("test kek")).keyMetadata().keyId();
        assertNotNull(keyId);
    }

    @Test
    void generateThenDecryptRoundTrips() {
        LocalKmsClient kms = new LocalKmsClient();
        GenerateDataKeyResponse dek = kms.generateDataKey(r -> r.keyId("kek-1").keySpec(DataKeySpec.AES_256));

        byte[] plaintext = dek.plaintext().asByteArray();
        assertEquals(32, plaintext.length);
        assertFalse(java.util.Arrays.equals(plaintext, dek.ciphertextBlob().asByteArray()),
            "wrapped DEK must differ from plaintext");

        byte[] decrypted = kms.decrypt(r -> r.keyId("kek-1").ciphertextBlob(dek.ciphertextBlob()))
            .plaintext().asByteArray();
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void numberOfBytesControlsDekLength() {
        GenerateDataKeyResponse dek = new LocalKmsClient()
            .generateDataKey(r -> r.keyId("kek-1").numberOfBytes(16));
        assertEquals(16, dek.plaintext().asByteArray().length);
    }

    @Test
    void tamperedBlobFailsAuthentication() {
        LocalKmsClient kms = new LocalKmsClient();
        byte[] wrapped = kms.generateDataKey(r -> r.keyId("kek-1").keySpec(DataKeySpec.AES_256))
            .ciphertextBlob().asByteArray();
        wrapped[wrapped.length - 1] ^= 0x01; // flip a bit in the GCM tag
        SdkBytes tampered = SdkBytes.fromByteArray(wrapped);

        assertThrows(ApplicationFailureException.class,
            () -> kms.decrypt(r -> r.keyId("kek-1").ciphertextBlob(tampered)));
    }

    @Test
    void missingKekPropertyThrows() {
        System.clearProperty(LocalKmsClient.KEK_PROPERTY);
        assertThrows(ApplicationFailureException.class,
            () -> new LocalKmsClient().generateDataKey(r -> r.keyId("kek-1").keySpec(DataKeySpec.AES_256)));
    }
}
