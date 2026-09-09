package dev.olegz.vf.registry.service.encryption;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.AesEncryptor;
import dev.olegz.vf.registry.dao.SigningKeyDao;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmFactoryFactory;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.EdDsaAlgorithm;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JwtServiceImpl implements JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtServiceImpl.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final int INITIAL_CACHE_CAPACITY = 30;
    private static final int ED25519_KEY_LENGTH = 32;
    static final String ED25519_ALGORITHM = "Ed25519";
    private static final String JCA_HMAC_SHA512_ALGORITHM = "HmacSHA512";
    private static final JsonMapper jwtMapper = BytesMapper.buildDefaultMapper();
    private static final long KEY_CACHE_REFRESH_INTERVAL = PropertyStore.getLong("vf.signingKey.cacheRefreshInterval", 900L) * 1000L;
    // Keep the activation delay longer than the refresh interval so every server can cache a new key before it is used.
    private static final long KEY_ACTIVATION_DELAY = PropertyStore.getLong(
        "vf.signingKey.activationDelay",
        KEY_CACHE_REFRESH_INTERVAL / 1000L + 100L) * 1000L;

    static {
        // jose4j 0.9.6 predates the fully specified Ed25519 JOSE algorithm identifier.
        AlgorithmFactoryFactory.getInstance()
            .getJwsAlgorithmFactory()
            .registerAlgorithm(new FullySpecifiedEd25519Algorithm());
    }

    private volatile List<Integer> activeHs512KeyIds = List.of();
    private volatile List<Integer> activeEd25519KeyIds = List.of();
    private final ConcurrentHashMap<Integer, JwtKey> jwtKeysById = new ConcurrentHashMap<>(INITIAL_CACHE_CAPACITY);

    private final AesEncryptor kekEncryptor;
    private ScheduledExecutorService keyCacheRefreshScheduler;

    // Time of the most recent cache refresh attempt.
    private volatile long lastKeyCacheRefreshTime = 0;

    private final SigningKeyDao signingKeyDao;

    public JwtServiceImpl(SigningKeyDao signingKeyDao) {
        this.signingKeyDao = signingKeyDao;

        byte[] kek = VigiloEnvironment.getKek();
        if (kek != null) {
            kekEncryptor = new AesEncryptor(kek);
        } else {
            kekEncryptor = null;
            logger.error("KEK is not set");
        }
    }

    /**
     * Loads persisted signing keys and provisions any missing signing algorithms.
     */
    @PostConstruct
    public void initializeSigningKeys() {
        try {
            refreshJwtKeyCache(true);
            ensureSigningKeysAvailable();
        } catch (Exception e) {
            logger.error("Exception in loading keys", e);
        }

        keyCacheRefreshScheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform()
                .daemon()
                .name("JwtKeyCacheRefresh")
                .unstarted(runnable)
        );
        keyCacheRefreshScheduler.scheduleAtFixedRate(
            () -> refreshJwtKeyCache(false),
            KEY_CACHE_REFRESH_INTERVAL,
            KEY_CACHE_REFRESH_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void shutdown() {
        if (keyCacheRefreshScheduler != null) {
            keyCacheRefreshScheduler.shutdownNow();
        }
    }

    private static final int INIT_KEYS_NUM = PropertyStore.getInt("vf.encryption.initKeys", 10);

    /**
     * Generates initial keys when either supported signing algorithm is unavailable.
     */
    private void ensureSigningKeysAvailable() {
        if (kekEncryptor == null) throw new ApplicationFailureException("KEK is not set");
        boolean updated = false;

        if (activeHs512KeyIds.isEmpty()) {
            int[] keyIds = generateAndStoreSigningKeys(INIT_KEYS_NUM, SigningAlgorithm.HS512);
            if ((keyIds == null) || (keyIds.length == 0)) {
                throw new ApplicationFailureException("HS512 JWT keys are not available");
            }
            updated = true;
        }

        if (activeEd25519KeyIds.isEmpty()) {
            int[] keyIds = generateAndStoreSigningKeys(INIT_KEYS_NUM, SigningAlgorithm.ED25519);
            if ((keyIds == null) || (keyIds.length == 0)) {
                throw new ApplicationFailureException("Ed25519 JWT keys are not available");
            }
            updated = true;
        }

        if (updated) reloadJwtKeyCache();
    }

    /**
     * Refreshes the JWT key cache when the refresh interval has elapsed.
     *
     * @param force {@code true} to refresh immediately
     */
    private synchronized void refreshJwtKeyCache(boolean force) {
        long currentTime = System.currentTimeMillis();
        if ((kekEncryptor == null) ||
            !force && (lastKeyCacheRefreshTime + KEY_CACHE_REFRESH_INTERVAL >= currentTime)) return;

        // Record the attempt before loading so subsequent non-forced calls do not immediately reload the cache.
        lastKeyCacheRefreshTime = currentTime;

        reloadJwtKeyCache();
    }

    /**
     * Reloads signing keys and active key identifiers from persistent storage.
     */
    private void reloadJwtKeyCache() {
        logger.debug("|>reloadJwtKeyCache()");

        try {
            List<SigningKey> signingKeys = signingKeyDao.getSigningKeys();
            if (signingKeys.isEmpty()) return;

            // Delay activation long enough for other server instances to cache newly created keys.
            long activationCutoffTime = System.currentTimeMillis() - KEY_ACTIVATION_DELAY;

            reloadHs512JwtKeyCache(signingKeys, activationCutoffTime);
            reloadEd25519JwtKeyCache(signingKeys, activationCutoffTime);
        } catch (Exception e) {
            logger.error("Exception in loading JWT keys", e);
        }

        logger.debug("|<reloadJwtKeyCache()");
    }

    /**
     * Updates the HS512 portion of the JWT key cache from the supplied persisted keys.
     */
    private void reloadHs512JwtKeyCache(List<SigningKey> signingKeys, long activationCutoffTime) {
        // Build a separate active-ID snapshot for atomic publication to readers.
        int initialCapacity = Math.max(activeHs512KeyIds.size(), INITIAL_CACHE_CAPACITY);
        HashSet<Integer> reloadedActiveKeyIds = new HashSet<>(initialCapacity);
        reloadedActiveKeyIds.addAll(activeHs512KeyIds);

        boolean updated = false;
        for (SigningKey signingKey : signingKeys) {
            if (signingKey.algorithm != SigningAlgorithm.HS512) continue;

            if (syncActiveKeyId(signingKey, activationCutoffTime, reloadedActiveKeyIds)) updated = true;
            cacheJwtKey(signingKey);
        }

        if (updated) activeHs512KeyIds = List.copyOf(reloadedActiveKeyIds);
    }

    /**
     * Updates the Ed25519 portion of the JWT key cache from the supplied persisted keys.
     */
    private void reloadEd25519JwtKeyCache(List<SigningKey> signingKeys, long activationCutoffTime) {
        // Build a separate active-ID snapshot for atomic publication to readers.
        int initialCapacity = Math.max(activeEd25519KeyIds.size(), INITIAL_CACHE_CAPACITY);
        HashSet<Integer> reloadedActiveKeyIds = new HashSet<>(initialCapacity);
        reloadedActiveKeyIds.addAll(activeEd25519KeyIds);

        boolean updated = false;
        for (SigningKey signingKey : signingKeys) {
            if (signingKey.algorithm != SigningAlgorithm.ED25519) continue;
            if (signingKey.publicKey == null) {
                logger.error("No public key for " + signingKey);
                continue;
            }

            if (syncActiveKeyId(signingKey, activationCutoffTime, reloadedActiveKeyIds)) updated = true;
            cacheJwtKey(signingKey);
        }

        if (updated) {
            activeEd25519KeyIds = List.copyOf(reloadedActiveKeyIds);
        }
    }

    /**
     * Decrypts and caches a signing key if it is not already present.
     */
    private void cacheJwtKey(SigningKey signingKey) {
        jwtKeysById.computeIfAbsent(signingKey.keyId, _ -> {
            try {
                byte[] key = kekEncryptor.decrypt(signingKey.encryptedKey);
                return key != null ? new JwtKey(signingKey, key) : null;
            } catch (Exception e) {
                logger.error("Exception in creating JWT key by id=" + signingKey.keyId, e);
            }
            return null;
        });
    }

    /**
     * Synchronizes one key's membership in the supplied active-ID snapshot.
     *
     * @return {@code true} when the set changed
     */
    private boolean syncActiveKeyId(
        SigningKey signingKey,
        long activationCutoffTime,
        Set<Integer> reloadedActiveKeyIds)
    {
        Integer keyId = signingKey.keyId;

        if (signingKey.active(activationCutoffTime)) {
            if (!reloadedActiveKeyIds.contains(keyId)) {
                logger.debug("| Add key: {}", signingKey);
                reloadedActiveKeyIds.add(keyId);
                return true;
            }
        } else if (reloadedActiveKeyIds.remove(keyId)) {
            logger.debug("| Disable key: {}", signingKey);
            return true;
        }

        return false;
    }

    /**
     * Generates, encrypts, and stores signing keys for the requested algorithm.
     */
    private int[] generateAndStoreSigningKeys(int keysNumber, SigningAlgorithm algorithm) {
        if (kekEncryptor == null) {
            throw new ApplicationFailureException("KEK is not set");
        }
        if (logger.isDebugEnabled()) {
            logger.debug(">generateAndStoreSigningKeys() keysNumber=" + keysNumber + ", algorithm=" + algorithm);
        }
        if (keysNumber <= 0) return null;

        int[] keyIds;

        try {
            KeyGenerator keyGenerator = null;
            KeyPairGenerator edKeyPairGenerator = null;

            switch (algorithm) {
                case HS512 -> keyGenerator = KeyGenerator.getInstance(JCA_HMAC_SHA512_ALGORITHM);
                case ED25519 -> {
                    edKeyPairGenerator = KeyPairGenerator.getInstance(ED25519_ALGORITHM);
                    edKeyPairGenerator.initialize(NamedParameterSpec.ED25519, secureRandom);
                }
                default -> throw new ApplicationFailureException("Unsupported signing algorithm " + algorithm);
            }

            keyIds = new int[keysNumber];

            for (int i = 0; i < keysNumber; i++) {
                // Generate the private signing-key material.
                SigningKey signingKey = new SigningKey();
                signingKey.algorithm = algorithm;
                signingKey.status = SigningKey.STATUS_ACTIVE;
                signingKey.createdAt = new Datetime(System.currentTimeMillis() - KEY_ACTIVATION_DELAY - 2000L);

                byte[] key;
                switch (algorithm) {
                    case HS512 -> {
                        SecretKey skey = keyGenerator.generateKey();
                        key = skey.getEncoded();
                    }
                    case ED25519 -> {
                        KeyPair edKeyPair = edKeyPairGenerator.generateKeyPair();
                        key = encodeEd25519PrivateKey(edKeyPair.getPrivate());
                        signingKey.publicKey = encodeEd25519PublicKey(edKeyPair.getPublic());
                    }
                    default -> throw new ApplicationFailureException("Unsupported signing algorithm " + algorithm);
                }

                signingKey.encryptedKey = kekEncryptor.encrypt(key);

                signingKeyDao.insertSigningKey(signingKey);
                keyIds[i] = signingKey.keyId;
            }

            refreshJwtKeyCache(true);
        } catch (Exception e) {
            logger.error("Exception in key generation for algorithm=" + algorithm, e);
            throw new ApplicationFailureException("Exception in key generation", e);
        }

        logger.debug("<generateAndStoreSigningKeys()");
        return keyIds;
    }

    /**
     * Selects a random active cached JWT key for the requested algorithm.
     */
    private JwtKey selectRandomActiveJwtKey(SigningAlgorithm algorithm) {
        List<Integer> keyIds = switch (algorithm) {
            case HS512 -> activeHs512KeyIds;
            case ED25519 -> activeEd25519KeyIds;
        };

        int keysSize = keyIds.size();
        if (keysSize == 0) return null;

        Integer keyId = keyIds.get(ThreadLocalRandom.current().nextInt(keysSize));
        return jwtKeysById.get(keyId);
    }

    // Preserve the issuer used by already-issued tokens; this is a wire identifier, not branding.
    private static final String OWN_ISSUER = "BotLab";

    @Override
    public String createJwt(JwtClaims claims, SigningAlgorithm algorithm) {
        JwtKey jwtKey = selectRandomActiveJwtKey(algorithm);
        if (jwtKey == null) {
            throw new ApplicationFailureException("JWT key not available, algorithm=" + algorithm);
        }

        claims.iss = OWN_ISSUER;

        return encodeAndSignJwt(jwtKey, claims);
    }

    /**
     * Encodes the claims and returns a compact JWS signed by the selected key.
     */
    private String encodeAndSignJwt(JwtKey jwtKey, JwtClaims claims) {
        try {
            return jwtKey.sign(jwtMapper.writeValueAsBytes(claims));
        } catch (Exception e) {
            throw new ApplicationFailureException("Exception in JWT generation", e);
        }
    }

    @Override
    public <T extends JwtClaims> T verifyJwt(
        String jwt,
        Class<T> claimsType)
    {
        JsonWebSignature jws = parseJwt(jwt);

        String keyIdHeader = jws.getKeyIdHeaderValue();
        if (keyIdHeader == null) throw new InvalidJwtException("Missing KID");
        int keyId;
        try {
            keyId = Integer.parseInt(keyIdHeader);
        } catch (NumberFormatException ee) {
            throw new InvalidJwtException("Wrong JWT KID");
        }

        JwtKey jwtKey = jwtKeysById.get(keyId);
        if (jwtKey == null) throw new InvalidJwtException("JWK not found");
        if (!jwtKey.acceptsAlgorithm(jws.getAlgorithmHeaderValue())) {
            throw new InvalidJwtException("Different JWT and JWK algorithms");
        }

        try {
            if (!jwtKey.verify(jws)) throw new InvalidJwtException("Wrong JWT signature");
        } catch (JoseException e) {
            throw new InvalidJwtException("Wrong JWT signature");
        }

        T claims;
        try {
            claims = jwtMapper.readValue(jws.getPayloadBytes(), claimsType);
        } catch (Exception e) {
            throw new InvalidJwtException("Cannot parse JWT claims");
        }

        if (isExpired(claims.exp, Instant.now())) throw new InvalidJwtException("Expired JWT");

        if (!OWN_ISSUER.equals(claims.iss)) throw new InvalidJwtException("Invalid JWT issuer");

        if (!claims.valid()) throw new InvalidJwtException("Invalid Claims");

        return claims;
    }

    static boolean isExpired(long expirationEpochSecond, Instant now) {
        return expirationEpochSecond <= now.getEpochSecond();
    }

    private static final int MIN_HEADER_LEN = 20;
    private static final int MAX_HEADER_LEN = 64;
    private static final int MIN_CLAIMS_LEN = 24;
    private static final int MAX_CLAIMS_LEN = 256;
    // HS512 and Ed25519 signatures are both 64 bytes, or 86 characters in unpadded Base64URL.
    private static final int MAX_SIGNATURE_LEN = 86;

    private JsonWebSignature parseJwt(String jwt) {
        if (jwt == null) throw new InvalidJwtException("Invalid JWT header");

        int dot0 = jwt.indexOf('.');
        if ((dot0 < MIN_HEADER_LEN) || (dot0 > MAX_HEADER_LEN)) {
            throw new InvalidJwtException("Invalid JWT header");
        }

        int dot1 = jwt.indexOf('.', dot0 + 1);
        if ((dot1 < 0) || (dot1 + 1 == jwt.length())) {
            throw new InvalidJwtException("Missing JWT signature");
        }
        if (jwt.length() - dot1 - 1 > MAX_SIGNATURE_LEN) {
            throw new InvalidJwtException("Invalid JWT signature");
        }
        int claimsLen = dot1 - dot0 - 1;
        if ((claimsLen < MIN_CLAIMS_LEN) || (claimsLen > MAX_CLAIMS_LEN)) {
            throw new InvalidJwtException("Invalid JWT payload");
        }

        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(jwt);
            return jws;
        } catch (JoseException e) {
            throw new InvalidJwtException("Cannot parse JWT header");
        }
    }

    static final class JwtKey {
        final int keyId;
        final String algorithm;
        final AlgorithmConstraints algorithmConstraints;
        final Key signingKey;
        final Key verificationKey;

        JwtKey(SigningKey signingKey, byte[] key) throws GeneralSecurityException {
            this.keyId = signingKey.keyId;

            switch (signingKey.algorithm) {
                case HS512 -> {
                    SecretKey secretKey = new SecretKeySpec(key, JCA_HMAC_SHA512_ALGORITHM);
                    this.algorithm = AlgorithmIdentifiers.HMAC_SHA512;
                    this.algorithmConstraints = permitAlgorithms(this.algorithm);
                    this.signingKey = secretKey;
                    this.verificationKey = secretKey;
                }
                case ED25519 -> {
                    this.algorithm = ED25519_ALGORITHM;
                    this.algorithmConstraints = permitAlgorithms(this.algorithm);
                    this.signingKey = decodeEd25519PrivateKey(key);
                    this.verificationKey = decodeEd25519PublicKey(signingKey.publicKey);
                }
                default -> throw new ApplicationFailureException("Unsupported signing algorithm " + signingKey);
            }
        }

        boolean acceptsAlgorithm(String algorithm) {
            return this.algorithm.equals(algorithm);
        }

        String sign(byte[] payload) throws JoseException {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(algorithm);
            jws.setAlgorithmConstraints(algorithmConstraints);
            jws.setKeyIdHeaderValue(Integer.toString(keyId));
            jws.setPayloadBytes(payload);
            jws.setKey(signingKey);
            return jws.getCompactSerialization();
        }

        boolean verify(JsonWebSignature jws) throws JoseException {
            jws.setAlgorithmConstraints(algorithmConstraints);
            jws.setKey(verificationKey);
            return jws.verifySignature();
        }
    }

    private static AlgorithmConstraints permitAlgorithms(String... algorithms) {
        return new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, algorithms);
    }

    private static final class FullySpecifiedEd25519Algorithm extends EdDsaAlgorithm {
        FullySpecifiedEd25519Algorithm() {
            setAlgorithmIdentifier(ED25519_ALGORITHM);
            setJavaAlgorithm(ED25519_ALGORITHM);
        }

        @Override
        public void validatePrivateKey(PrivateKey privateKey) throws org.jose4j.lang.InvalidKeyException {
            super.validatePrivateKey(privateKey);
            if (!(privateKey instanceof EdECPrivateKey edPrivateKey) ||
                !NamedParameterSpec.ED25519.getName().equals(edPrivateKey.getParams().getName()))
            {
                throw new org.jose4j.lang.InvalidKeyException("Ed25519 requires an Ed25519 private key");
            }
        }

        @Override
        public void validatePublicKey(PublicKey publicKey) throws org.jose4j.lang.InvalidKeyException {
            super.validatePublicKey(publicKey);
            if (!(publicKey instanceof EdECPublicKey edPublicKey) ||
                !NamedParameterSpec.ED25519.getName().equals(edPublicKey.getParams().getName()))
            {
                throw new org.jose4j.lang.InvalidKeyException("Ed25519 requires an Ed25519 public key");
            }
        }
    }

    static byte[] encodeEd25519PrivateKey(PrivateKey privateKey) {
        if (!(privateKey instanceof EdECPrivateKey edPrivateKey)) {
            throw new IllegalArgumentException("Not an Ed25519 private key");
        }

        byte[] key = edPrivateKey.getBytes()
            .orElseThrow(() -> new IllegalArgumentException("Ed25519 private key bytes are not available"));
        if (key.length != ED25519_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid Ed25519 private key length: " + key.length);
        }
        return key;
    }

    static byte[] encodeEd25519PublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof EdECPublicKey edPublicKey)) {
            throw new IllegalArgumentException("Not an Ed25519 public key");
        }

        EdECPoint point = edPublicKey.getPoint();
        byte[] y = point.getY().toByteArray();
        byte[] key = new byte[ED25519_KEY_LENGTH];
        int bytesToCopy = Math.min(y.length, key.length);
        for (int i = 0; i < bytesToCopy; i++) {
            key[i] = y[y.length - i - 1];
        }
        if (point.isXOdd()) key[key.length - 1] |= (byte) 0x80;
        return key;
    }

    static PrivateKey decodeEd25519PrivateKey(byte[] key) throws GeneralSecurityException {
        requireEd25519KeyLength(key, "private");
        KeyFactory keyFactory = KeyFactory.getInstance(ED25519_ALGORITHM);
        return keyFactory.generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, key));
    }

    static PublicKey decodeEd25519PublicKey(byte[] key) throws GeneralSecurityException {
        requireEd25519KeyLength(key, "public");

        byte[] yLittleEndian = key.clone();
        boolean xOdd = (yLittleEndian[yLittleEndian.length - 1] & 0x80) != 0;
        yLittleEndian[yLittleEndian.length - 1] &= 0x7f;

        byte[] yBigEndian = new byte[yLittleEndian.length];
        for (int i = 0; i < yLittleEndian.length; i++) {
            yBigEndian[i] = yLittleEndian[yLittleEndian.length - i - 1];
        }

        EdECPoint point = new EdECPoint(xOdd, new BigInteger(1, yBigEndian));
        KeyFactory keyFactory = KeyFactory.getInstance(ED25519_ALGORITHM);
        return keyFactory.generatePublic(new EdECPublicKeySpec(NamedParameterSpec.ED25519, point));
    }

    private static void requireEd25519KeyLength(byte[] key, String keyType) {
        if (key == null || key.length != ED25519_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "Invalid Ed25519 " + keyType + " key length: " + (key == null ? 0 : key.length));
        }
    }

}
