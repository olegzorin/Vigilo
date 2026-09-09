package dev.olegz.vf.registry.service.account;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.exception.UserExistsException;
import dev.olegz.vf.common.exception.WeakPasswordException;
import dev.olegz.vf.common.exception.WrongUserPasswordException;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.security.PasswordHasher;
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
 * Tests for {@link UserService}. Each test creates its own users through the service and runs
 * inside a transaction that is rolled back afterwards, so no data leaks between tests or into the
 * database. The focus is the password lifecycle: passwords are hashed on the way in, never stored
 * in plaintext, verified on authentication, and only changed through the dedicated path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class UserServiceImplTest {

    private static final String VALID_PASSWORD = "Secur3Pass!";
    private static final String OTHER_PASSWORD = "N3wSecret?9";

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final UserService userService;
    private final UserDao userDao;

    @Autowired
    UserServiceImplTest(UserService userService, UserDao userDao) {
        this.userService = userService;
        this.userDao = userDao;
    }

    private User createUser(String username, String rawPassword) {
        User user = new User();
        user.username = username;
        userService.createUser(user, rawPassword);
        return user;
    }

    // --- createUser --------------------------------------------------------------------

    @Test
    void createUser_storesHashNotPlaintext() {
        User user = createUser("alice", VALID_PASSWORD);

        assertTrue(user.userId > 0);
        assertNotNull(user.password);
        assertNotEquals(VALID_PASSWORD, user.password);
        // The stored value must be a verifiable hash of the raw password.
        assertTrue(user.password.startsWith("$argon2id$"));
        assertTrue(PasswordHasher.verifyPassword(VALID_PASSWORD, user.password));

        // And it must be what actually landed in the database.
        User reloaded = userDao.getUser(user.userId);
        assertNotNull(reloaded);
        assertEquals(user.password, reloaded.password);
    }

    @Test
    void createUser_duplicateUsername_throwsUserExists() {
        createUser("bob", VALID_PASSWORD);

        User dup = new User();
        dup.username = "bob";
        assertThrows(UserExistsException.class, () -> userService.createUser(dup, VALID_PASSWORD));
    }

    @Test
    void createUser_tooShortPassword_throwsWeakPassword_andPersistsNothing() {
        User user = new User();
        user.username = "carol";
        assertThrows(WeakPasswordException.class, () -> userService.createUser(user, "short"));
        assertEquals(0, user.userId);
    }

    @Test
    void createUser_cyclicPassword_throwsWeakPassword() {
        User user = new User();
        user.username = "dave";
        assertThrows(WeakPasswordException.class, () -> userService.createUser(user, "aaaaaaaa"));
    }

    // --- getUser -----------------------------------------------------------------------

    @Test
    void getUser_returnsStoredUser() {
        User created = createUser("kate", VALID_PASSWORD);

        User found = userService.getUser(created.userId);

        assertNotNull(found);
        assertEquals(created.userId, found.userId);
        assertEquals("kate", found.username);
    }

    @Test
    void getUser_unknownId_returnsNull() {
        assertNull(userService.getUser(-1));
    }

    // --- authenticate ------------------------------------------------------------------

    @Test
    void authenticate_correctPassword_returnsUser() {
        User created = createUser("erin", VALID_PASSWORD);

        User authenticated = userService.authenticate("erin", VALID_PASSWORD);

        assertNotNull(authenticated);
        assertEquals(created.userId, authenticated.userId);
    }

    @Test
    void authenticate_wrongPassword_throwsWrongUserPassword() {
        createUser("frank", VALID_PASSWORD);

        assertThrows(WrongUserPasswordException.class,
                () -> userService.authenticate("frank", "WrongPass1!"));
    }

    @Test
    void authenticate_unknownUser_throwsWrongUserPassword() {
        assertThrows(WrongUserPasswordException.class,
                () -> userService.authenticate("does-not-exist", VALID_PASSWORD));
    }

    // --- changePassword ----------------------------------------------------------------

    @Test
    void changePassword_replacesHash_oldNoLongerValid_newValid() {
        User user = createUser("grace", VALID_PASSWORD);

        assertTrue(userService.changePassword(user.userId, OTHER_PASSWORD));

        User reloaded = userDao.getUser(user.userId);
        assertNotNull(reloaded);
        assertNotEquals(user.password, reloaded.password);
        assertTrue(PasswordHasher.verifyPassword(OTHER_PASSWORD, reloaded.password));

        userService.authenticate("grace", OTHER_PASSWORD);
        assertThrows(WrongUserPasswordException.class,
                () -> userService.authenticate("grace", VALID_PASSWORD));
    }

    @Test
    void changePassword_weakPassword_throwsAndLeavesOldPassword() {
        User user = createUser("heidi", VALID_PASSWORD);
        String originalHash = user.password;

        assertThrows(WeakPasswordException.class,
                () -> userService.changePassword(user.userId, "short"));

        User reloaded = userDao.getUser(user.userId);
        assertEquals(originalHash, reloaded.password);
    }

    @Test
    void changePassword_unknownUser_returnsFalse() {
        assertEquals(false, userService.changePassword(-1, VALID_PASSWORD));
    }

    // --- updateProfile -----------------------------------------------------------------

    @Test
    void updateProfile_updatesContactFields_returnsUpdatedUser() {
        User created = createUser("ivan", VALID_PASSWORD);

        User changes = new User();
        changes.userId = created.userId;
        changes.firstName = "Ivan";
        changes.email = "ivan@example.com";
        User updated = userService.updateProfile(changes);

        assertNotNull(updated);
        assertEquals("Ivan", updated.firstName);
        assertEquals("ivan@example.com", updated.email);

        User reloaded = userDao.getUser(created.userId);
        assertEquals("Ivan", reloaded.firstName);
        assertEquals("ivan@example.com", reloaded.email);
    }

    @Test
    void updateProfile_preservesCredentialsAndImmutableFields() {
        User created = createUser("judy", VALID_PASSWORD);
        String originalHash = created.password;
        Datetime originalCreatedAt = created.createdAt;

        // A caller (e.g. the REST layer) that sends only profile fields: no username,
        // password or dates. These must survive the update.
        User changes = new User();
        changes.userId = created.userId;
        changes.firstName = "Judy";
        userService.updateProfile(changes);

        User reloaded = userDao.getUser(created.userId);
        assertEquals("Judy", reloaded.firstName);
        assertEquals("judy", reloaded.username);
        assertNotNull(reloaded.createdAt);
        assertEquals(originalCreatedAt, reloaded.createdAt);
        assertEquals(originalHash, reloaded.password);
        assertTrue(PasswordHasher.verifyPassword(VALID_PASSWORD, reloaded.password));
    }

    @Test
    void updateProfile_unknownUser_returnsNull() {
        User changes = new User();
        changes.userId = -1;
        changes.firstName = "Nobody";
        assertNull(userService.updateProfile(changes));
    }
}
