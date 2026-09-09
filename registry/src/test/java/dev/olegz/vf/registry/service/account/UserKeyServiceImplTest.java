package dev.olegz.vf.registry.service.account;

import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.registry.TestConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the rejection path of {@link UserKeyService}: a missing or malformed key must be turned
 * into an {@link InvalidJwtException} (which the API maps to HTTP 401) rather than leaking a
 * lower-level parsing failure.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
class UserKeyServiceImplTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final UserKeyService userKeyService;

    @Autowired
    UserKeyServiceImplTest(UserKeyService userKeyService) {
        this.userKeyService = userKeyService;
    }

    @Test
    void parseUserKey_null_throwsInvalidJwt() {
        assertThrows(InvalidJwtException.class, () -> userKeyService.parseUserKey(null));
    }

    @Test
    void parseUserKey_blank_throwsInvalidJwt() {
        assertThrows(InvalidJwtException.class, () -> userKeyService.parseUserKey("   "));
    }

    @Test
    void parseUserKey_malformed_throwsInvalidJwt() {
        assertThrows(InvalidJwtException.class, () -> userKeyService.parseUserKey("not-a-jwt"));
    }
}
