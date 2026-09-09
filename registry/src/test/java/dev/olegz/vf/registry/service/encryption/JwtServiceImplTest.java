package dev.olegz.vf.registry.service.encryption;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.registry.domain.account.UserKeyJwtClaims;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceImplTest {
    private static final HexFormat HEX = HexFormat.of();

    // RFC 8032, section 7.1, test vector 1. These are the same raw 32-byte formats persisted by Vigilo Framework.
    private static final byte[] PRIVATE_KEY = HEX.parseHex(
        "9d61b19deffd5a60ba844af492ec2cc4" +
            "4449c5697b326919703bac031cae7f60");
    private static final byte[] PUBLIC_KEY = HEX.parseHex(
        "d75a980182b10ab7d54bfed3c964073a" +
            "0ee172f3daa62325af021a68f707511a");
    private static final byte[] SIGNATURE = HEX.parseHex(
        "e5564300c360ac729086e2cc806e828a" +
            "84877f1eb8e5d974d873e06522490155" +
            "5fb8821590a33bacc61e39701cf9b46b" +
            "d25bf5f0595bbe24655141438e7a100b");

    @Test
    void supportsPersistedRawEd25519KeyFormat() throws Exception {
        PrivateKey privateKey = JwtServiceImpl.decodeEd25519PrivateKey(PRIVATE_KEY);
        PublicKey publicKey = JwtServiceImpl.decodeEd25519PublicKey(PUBLIC_KEY);

        assertArrayEquals(PRIVATE_KEY, JwtServiceImpl.encodeEd25519PrivateKey(privateKey));
        assertArrayEquals(PUBLIC_KEY, JwtServiceImpl.encodeEd25519PublicKey(publicKey));

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        byte[] signature = signer.sign();

        assertArrayEquals(SIGNATURE, signature);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        assertTrue(verifier.verify(SIGNATURE));

        byte[] invalidSignature = SIGNATURE.clone();
        invalidSignature[0] ^= 1;
        verifier.initVerify(publicKey);
        assertFalse(verifier.verify(invalidSignature));
    }

    @Test
    void preservesRawFormatForNewJcaKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        generator.initialize(NamedParameterSpec.ED25519);
        KeyPair generated = generator.generateKeyPair();

        byte[] privateKeyBytes = JwtServiceImpl.encodeEd25519PrivateKey(generated.getPrivate());
        byte[] publicKeyBytes = JwtServiceImpl.encodeEd25519PublicKey(generated.getPublic());
        PrivateKey decodedPrivateKey = JwtServiceImpl.decodeEd25519PrivateKey(privateKeyBytes);
        PublicKey decodedPublicKey = JwtServiceImpl.decodeEd25519PublicKey(publicKeyBytes);

        byte[] message = "Vigilo Framework Ed25519 compatibility".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(decodedPrivateKey);
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(decodedPublicKey);
        verifier.update(message);
        assertTrue(verifier.verify(signature));
    }

    @Test
    void checksExpirationInEpochSecondsWithoutOverflow() {
        Instant now = Instant.ofEpochSecond(1_000, 999_999_999);

        assertTrue(JwtServiceImpl.isExpired(1_000, now));
        assertTrue(JwtServiceImpl.isExpired(999, now));
        assertFalse(JwtServiceImpl.isExpired(1_001, now));
        assertFalse(JwtServiceImpl.isExpired(Long.MAX_VALUE, now));
    }

    @Test
    void signsAndVerifiesFullySpecifiedEd25519() throws Exception {
        JwtServiceImpl.JwtKey jwtKey = newEd25519JwtKey();
        byte[] payload = "{\"iss\":\"BotLab\",\"exp\":2000000000,\"ty\":0,\"uid\":7}"
            .getBytes(StandardCharsets.UTF_8);

        String token = jwtKey.sign(payload);
        JsonWebSignature parsedToken = parse(token);

        assertEquals(JwtServiceImpl.ED25519_ALGORITHM, parsedToken.getAlgorithmHeaderValue());
        assertEquals("17", parsedToken.getKeyIdHeaderValue());
        assertTrue(jwtKey.acceptsAlgorithm(parsedToken.getAlgorithmHeaderValue()));
        assertTrue(jwtKey.verify(parsedToken));
        assertArrayEquals(payload, parsedToken.getUnverifiedPayloadBytes());
        assertJcaEd25519Signature(token);
        assertFalse(jwtKey.acceptsAlgorithm(AlgorithmIdentifiers.EDDSA));
        assertFalse(jwtKey.acceptsAlgorithm(AlgorithmIdentifiers.HMAC_SHA512));
    }

    @Test
    void signsAndVerifiesHs512WithJose4j() throws Exception {
        byte[] hmacKeyBytes = new byte[64];
        Arrays.fill(hmacKeyBytes, (byte) 0x5a);

        SigningKey signingKey = new SigningKey();
        signingKey.keyId = 23;
        signingKey.algorithm = SigningAlgorithm.HS512;
        JwtServiceImpl.JwtKey jwtKey = new JwtServiceImpl.JwtKey(signingKey, hmacKeyBytes);

        byte[] payload = "{\"iss\":\"BotLab\",\"exp\":2000000000,\"ty\":1,\"aid\":1,\"bid\":2}"
            .getBytes(StandardCharsets.UTF_8);
        JsonWebSignature parsedToken = parse(jwtKey.sign(payload));

        assertEquals(AlgorithmIdentifiers.HMAC_SHA512, parsedToken.getAlgorithmHeaderValue());
        assertEquals("23", parsedToken.getKeyIdHeaderValue());
        assertTrue(jwtKey.acceptsAlgorithm(parsedToken.getAlgorithmHeaderValue()));
        assertTrue(jwtKey.verify(parsedToken));
        assertFalse(jwtKey.acceptsAlgorithm(JwtServiceImpl.ED25519_ALGORITHM));
    }

    @Test
    void rejectsOversizedSignatureBeforeJoseParsing() {
        JwtServiceImpl jwtService = new JwtServiceImpl(null);
        String token = "a".repeat(20) + "." + "a".repeat(24) + "." + "a".repeat(87);

        assertInvalidJwtMessage(jwtService, token, "Invalid JWT signature");
    }

    @Test
    void rejectsOversizedHeaderAndPayloadBeforeJoseParsing() {
        JwtServiceImpl jwtService = new JwtServiceImpl(null);

        assertInvalidJwtMessage(
            jwtService,
            "a".repeat(65) + "." + "a".repeat(24) + "." + "a".repeat(86),
            "Invalid JWT header");
        assertInvalidJwtMessage(
            jwtService,
            "a".repeat(20) + "." + "a".repeat(257) + "." + "a".repeat(86),
            "Invalid JWT payload");
    }

    private static void assertInvalidJwtMessage(
        JwtServiceImpl jwtService,
        String token,
        String expectedMessage)
    {
        InvalidJwtException exception = assertThrows(
            InvalidJwtException.class,
            () -> jwtService.verifyJwt(token, UserKeyJwtClaims.class));

        assertEquals(expectedMessage, exception.getApiErrorMessage());
    }

    private static JwtServiceImpl.JwtKey newEd25519JwtKey() throws Exception {
        SigningKey signingKey = new SigningKey();
        signingKey.keyId = 17;
        signingKey.algorithm = SigningAlgorithm.ED25519;
        signingKey.publicKey = PUBLIC_KEY;
        return new JwtServiceImpl.JwtKey(signingKey, PRIVATE_KEY);
    }

    private static JsonWebSignature parse(String token) throws Exception {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setCompactSerialization(token);
        return jws;
    }

    private static void assertJcaEd25519Signature(String token) throws Exception {
        int signatureSeparator = token.lastIndexOf('.');
        byte[] signingInput = token.substring(0, signatureSeparator).getBytes(StandardCharsets.US_ASCII);
        byte[] signature = Base64.getUrlDecoder().decode(token.substring(signatureSeparator + 1));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(JwtServiceImpl.decodeEd25519PublicKey(PUBLIC_KEY));
        verifier.update(signingInput);
        assertTrue(verifier.verify(signature));
    }
}
