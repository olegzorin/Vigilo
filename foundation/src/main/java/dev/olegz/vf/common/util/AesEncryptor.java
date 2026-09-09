package dev.olegz.vf.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES/GCM encryption and decryption using the Java Cryptography Extension.
 * Byte-array results contain the random IV followed by the ciphertext and authentication tag.
 * String operations encode that byte-array format as Base64.
 */
public final class AesEncryptor {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String KEY_ALGORITHM = "AES"; // encryption key algorithm
    private static final String TRANSFORMATION_GCM = "AES/GCM/NoPadding";
    // In AES/GCM, the IV is typically 12 bytes long (96 bits), which is the recommended size for performance and security
    private static final int GCM_IV_SIZE = 12;
    // GCM requires a 128-bit authentication tag to ensure data integrity
    private static final int GCM_TAG_LEN = 128;

    private final transient SecretKeySpec keySpec;

    public AesEncryptor(byte[] key) {
        this.keySpec = new SecretKeySpec(key, KEY_ALGORITHM);
    }

    /**
     * Encrypt a string value and encode the encrypted bytes as Base64.
     * @param input input value
     * @return encrypted value
     */
    public String encrypt(String input) throws GeneralSecurityException {
        byte[] encrypted = encrypt(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Encrypt a byte array.
     * @param input input value
     * @return encrypted bytes prefixed with the IV
     */
    public byte[] encrypt(byte[] input) throws GeneralSecurityException {
        // generate a random IV for each encrypted data
        byte[] iv = new byte[GCM_IV_SIZE];
        secureRandom.nextBytes(iv);
        GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LEN, iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        int encryptedSize = cipher.getOutputSize(input.length);
        byte[] encrypted = new byte[iv.length + encryptedSize];
        // concatenate IV and encrypted data
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        cipher.doFinal(input, 0, input.length, encrypted, iv.length);

        return encrypted;
    }

    /**
     * Decode and decrypt a Base64 string value.
     * @param input encrypted value
     * @return decrypted value
     */
    public String decrypt(String input) throws GeneralSecurityException {
        byte[] encrypted = Base64.getDecoder().decode(input);
        return new String(decrypt(encrypted), StandardCharsets.UTF_8);
    }

    /**
     * Decrypt bytes prefixed with the IV.
     * @param encrypted encrypted bytes
     * @return decrypted value
     */
    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted.length <= GCM_IV_SIZE) throw new GeneralSecurityException("Too short encrypted data");

        // extract IV from the beginning
        GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LEN, encrypted, 0, GCM_IV_SIZE);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        // decrypt the rest
        return cipher.doFinal(encrypted, GCM_IV_SIZE, encrypted.length - GCM_IV_SIZE);
    }
}
