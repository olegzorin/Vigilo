package dev.olegz.vf.registry.dao;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.service.encryption.SigningAlgorithm;
import dev.olegz.vf.registry.service.encryption.SigningKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link SigningKeyDao}. The query scans the whole {@code signing_keys} table,
 * so each test seeds its own keys and looks them up by generated {@code keyId}. Everything runs
 * inside a transaction that is rolled back afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class SigningKeyDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final SigningKeyDao signingKeyDao;

    @Autowired
    SigningKeyDaoTest(SigningKeyDao signingKeyDao) {
        this.signingKeyDao = signingKeyDao;
    }

    private static SigningKey newKey(SigningAlgorithm algorithm, byte status) {
        SigningKey key = new SigningKey();
        key.algorithm = algorithm;
        key.status = status;
        key.encryptedKey = new byte[] {1, 2, 3, 4};
        key.publicKey = new byte[] {5, 6, 7};
        key.createdAt = Datetime.now();
        return key;
    }

    private SigningKey findSigningKey(int keyId) {
        return CollectionOps.findAny(signingKeyDao.getSigningKeys(), key -> key.keyId == keyId);
    }

    @Test
    void insert_jwtKeys_appearInJwtKeys() {
        SigningKey hmacKey = newKey(SigningAlgorithm.HS512, SigningKey.STATUS_ACTIVE);
        SigningKey edKey = newKey(SigningAlgorithm.ED25519, SigningKey.STATUS_DEACTIVATED);

        signingKeyDao.insertSigningKey(hmacKey);
        signingKeyDao.insertSigningKey(edKey);

        assertTrue(hmacKey.keyId > 0);
        SigningKey loadedHmacKey = findSigningKey(hmacKey.keyId);
        assertNotNull(loadedHmacKey);
        assertEquals(SigningAlgorithm.HS512, loadedHmacKey.algorithm);
        assertEquals(SigningKey.STATUS_ACTIVE, loadedHmacKey.status);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, loadedHmacKey.encryptedKey);
        assertNotNull(loadedHmacKey.createdAt);

        SigningKey loadedEdKey = findSigningKey(edKey.keyId);
        assertNotNull(loadedEdKey);
        assertEquals(SigningAlgorithm.ED25519, loadedEdKey.algorithm);
        assertEquals(SigningKey.STATUS_DEACTIVATED, loadedEdKey.status);
        assertArrayEquals(new byte[] {5, 6, 7}, loadedEdKey.publicKey);
    }
}
