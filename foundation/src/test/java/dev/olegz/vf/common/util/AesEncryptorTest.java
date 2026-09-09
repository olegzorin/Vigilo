package dev.olegz.vf.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AesEncryptorTest {

    private static final byte[] KEY = {
        0, 1, 2, 3, 4, 5, 6, 7,
        8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, 26, 27, 28, 29, 30, 31
    };

    @Test
    void byteArrayRoundTripUsesRandomIv() throws GeneralSecurityException {
        AesEncryptor encryptor = new AesEncryptor(KEY);
        byte[] input = "binary \u0000 value".getBytes(StandardCharsets.UTF_8);

        byte[] first = encryptor.encrypt(input);
        byte[] second = encryptor.encrypt(input);

        assertFalse(Arrays.equals(first, second));
        assertArrayEquals(input, encryptor.decrypt(first));
        assertArrayEquals(input, encryptor.decrypt(second));
    }

    @Test
    void stringAndByteArrayApisUseTheSameFormat() throws GeneralSecurityException {
        AesEncryptor encryptor = new AesEncryptor(KEY);
        String input = "Зашифрованное значение";
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

        String encryptedString = encryptor.encrypt(input);
        assertArrayEquals(inputBytes, encryptor.decrypt(Base64.getDecoder().decode(encryptedString)));

        byte[] encryptedBytes = encryptor.encrypt(inputBytes);
        assertEquals(input, encryptor.decrypt(Base64.getEncoder().encodeToString(encryptedBytes)));
    }
}
