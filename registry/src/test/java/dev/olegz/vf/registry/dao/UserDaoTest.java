package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.domain.account.User;
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
 * DAO tests for {@link UserDao}. Each test seeds its own users - with a unique username, since
 * {@code getUserByUsername} scans the whole table - and runs inside a transaction that is rolled
 * back afterwards, so no data leaks between tests or into the database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class UserDaoTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final UserDao userDao;

    @Autowired
    UserDaoTest(UserDao userDao) {
        this.userDao = userDao;
    }

    /** A username that will not collide with any other row in the (shared) table. */
    private static String uniqueUsername() {
        return "user_" + System.nanoTime();
    }

    private static User newUser(String username) {
        User user = new User();
        user.username = username;
        user.password = "hash-original";
        user.firstName = "Ada";
        user.lastName = "Lovelace";
        user.email = "ada@example.com";
        user.phone = "+1-555-0100";
        user.organizationId = 42;
        return user;
    }

    @Test
    void insertThenGet_roundTripsAllFields() {
        String username = uniqueUsername();
        User user = newUser(username);

        userDao.insertUser(user);

        assertTrue(user.userId > 0);
        assertNotNull(user.createdAt, "createdAt should default to now on insert");

        User loaded = userDao.getUser(user.userId);
        assertNotNull(loaded);
        assertEquals(user.userId, loaded.userId);
        assertEquals(username, loaded.username);
        assertEquals("hash-original", loaded.password);
        assertEquals("Ada", loaded.firstName);
        assertEquals("Lovelace", loaded.lastName);
        assertEquals("ada@example.com", loaded.email);
        assertEquals("+1-555-0100", loaded.phone);
        assertEquals(42, loaded.organizationId);
        assertNotNull(loaded.createdAt);
        assertNull(loaded.deletedAt);
    }

    @Test
    void insert_keepsExplicitCreatedAt() {
        User user = newUser(uniqueUsername());
        // The created_at column is a second-precision datetime, so avoid sub-second values.
        Datetime createdAt = Datetime.nowMinusDays(3);
        user.createdAt = createdAt;

        userDao.insertUser(user);

        User loaded = userDao.getUser(user.userId);
        assertEquals(createdAt, loaded.createdAt);
    }

    @Test
    void getUnknownId_returnsNull() {
        assertNull(userDao.getUser(-1));
    }

    @Test
    void getUser_excludesUserWithFutureDeletionTimestamp() {
        User user = newUser(uniqueUsername());
        user.deletedAt = Datetime.nowPlusDays(1);
        userDao.insertUser(user);

        assertNull(userDao.getUser(user.userId));
    }

    @Test
    void getUsers_appliesScopeAndCaseInsensitivePartialFilters() {
        User ada = newUser(uniqueUsername());
        ada.firstName = "Ada-Marie";
        ada.lastName = "Lovelace";
        ada.email = "Ada@Example.com";
        userDao.insertUser(ada);

        User grace = newUser(uniqueUsername());
        grace.firstName = "Grace";
        grace.lastName = "Hopper";
        grace.email = "grace@example.com";
        userDao.insertUser(grace);

        User otherOrganization = newUser(uniqueUsername());
        otherOrganization.organizationId = 77;
        userDao.insertUser(otherOrganization);

        User deleted = newUser(uniqueUsername());
        deleted.firstName = "Ada-Marie";
        deleted.deletedAt = Datetime.nowPlusDays(1);
        userDao.insertUser(deleted);

        List<User> organizationUsers = userDao.getUsers(42, null, null, null, null);
        assertEquals(2, organizationUsers.size());

        List<User> filtered = userDao.getUsers(42, null, "DA-m", "LOVE", "EXAMPLE.COM");
        assertEquals(1, filtered.size());
        assertEquals(ada.userId, filtered.getFirst().userId);

        List<User> ownAccount = userDao.getUsers(null, grace.userId, "rac", "opp", "ACE@");
        assertEquals(1, ownAccount.size());
        assertEquals(grace.userId, ownAccount.getFirst().userId);

        assertTrue(userDao.getUsers(null, grace.userId, "Ada", null, null).isEmpty());
    }

    @Test
    void getUserByUsername_returnsNonDeletedUser() {
        String username = uniqueUsername();
        User user = newUser(username);
        userDao.insertUser(user);

        User loaded = userDao.getUserByUsername(username);
        assertNotNull(loaded);
        assertEquals(user.userId, loaded.userId);
        assertEquals(username, loaded.username);
    }

    @Test
    void getUserByUsername_unknown_returnsNull() {
        assertNull(userDao.getUserByUsername(uniqueUsername()));
    }

    @Test
    void getUserByUsername_returnsEarliestNonDeleted_whenDuplicates() {
        String username = uniqueUsername();

        User earlier = newUser(username);
        earlier.createdAt = Datetime.nowMinusDays(5);
        userDao.insertUser(earlier);

        User later = newUser(username);
        later.createdAt = Datetime.nowMinusDays(1);
        userDao.insertUser(later);

        // selectUserByUsername orders by created_at and returns the first row.
        User loaded = userDao.getUserByUsername(username);
        assertNotNull(loaded);
        assertEquals(earlier.userId, loaded.userId);
    }

    @Test
    void getUserByUsername_excludesDeletedUser() {
        String username = uniqueUsername();
        User user = newUser(username);
        user.deletedAt = Datetime.nowMinusDays(1);
        userDao.insertUser(user);

        assertNull(userDao.getUserByUsername(username));
    }

    @Test
    void update_changesMutableFields() {
        User user = newUser(uniqueUsername());
        userDao.insertUser(user);
        Datetime originalCreatedAt = user.createdAt;

        String newUsername = uniqueUsername();
        user.username = newUsername;
        user.firstName = "Grace";
        user.lastName = "Hopper";
        user.email = "grace@example.com";
        user.phone = "+1-555-0199";
        user.organizationId = 77;
        user.createdAt = Datetime.nowMinusDays(10);

        assertTrue(userDao.updateUser(user));

        User loaded = userDao.getUser(user.userId);
        assertEquals(newUsername, loaded.username);
        assertEquals("Grace", loaded.firstName);
        assertEquals("Hopper", loaded.lastName);
        assertEquals("grace@example.com", loaded.email);
        assertEquals("+1-555-0199", loaded.phone);
        assertEquals(77, loaded.organizationId);
        assertEquals(originalCreatedAt, loaded.createdAt);
    }

    @Test
    void update_doesNotTouchPassword() {
        User user = newUser(uniqueUsername());
        userDao.insertUser(user);

        user.firstName = "Changed";
        assertTrue(userDao.updateUser(user));

        // updateUser must not overwrite the stored password.
        assertEquals("hash-original", userDao.getUser(user.userId).password);
    }

    @Test
    void update_unknownUser_returnsFalse() {
        User user = newUser(uniqueUsername());
        user.userId = -1;
        user.createdAt = Datetime.nowMinusDays(1);

        assertFalse(userDao.updateUser(user));
    }

    @Test
    void updatePassword_updatesNonDeletedUser() {
        User user = newUser(uniqueUsername());
        userDao.insertUser(user);

        assertTrue(userDao.updatePassword(user.userId, "hash-new"));
        assertEquals("hash-new", userDao.getUser(user.userId).password);
    }

    @Test
    void updatePassword_unknownUser_returnsFalse() {
        assertFalse(userDao.updatePassword(-1, "hash-new"));
    }

    @Test
    void updatePassword_deletedUser_returnsFalse() {
        User user = newUser(uniqueUsername());
        user.deletedAt = Datetime.nowPlusDays(1);
        userDao.insertUser(user);

        assertFalse(userDao.updatePassword(user.userId, "hash-new"));
    }

    @Test
    void update_withDeletedAtPermanentlyDeletesUser() {
        User user = newUser(uniqueUsername());
        userDao.insertUser(user);
        user.deletedAt = Datetime.now();

        assertTrue(userDao.updateUser(user));
        assertNull(userDao.getUser(user.userId));
        assertFalse(userDao.updateUser(user));
        assertFalse(userDao.updatePassword(user.userId, "hash-new"));
    }

    @Test
    void delete_marksUserDeleted_soGetReturnsNull() {
        User user = newUser(uniqueUsername());
        userDao.insertUser(user);

        assertTrue(userDao.deleteUser(user.userId));
        assertNull(userDao.getUser(user.userId));
        // Deletion is final.
        assertFalse(userDao.deleteUser(user.userId));
    }

    @Test
    void delete_unknownUser_returnsFalse() {
        assertFalse(userDao.deleteUser(-1));
    }
}
