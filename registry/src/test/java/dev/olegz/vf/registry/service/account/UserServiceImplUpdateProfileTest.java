package dev.olegz.vf.registry.service.account;

import java.util.List;

import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserServiceImplUpdateProfileTest {

    @Test
    void updateProfile_appliesOnlyRealChangesAndUpdatesDaoOnce() {
        User existing = user(1);
        existing.firstName = "Existing first name";
        existing.lastName = "Existing last name";
        existing.email = "existing@example.com";
        existing.phone = "123";
        RecordingUserDao userDao = new RecordingUserDao(existing);

        User requested = user(1);
        requested.firstName = null;
        requested.lastName = "Existing last name";
        requested.email = "";
        requested.phone = "456";

        User updated = new UserServiceImpl(userDao).updateProfile(requested);

        assertSame(existing, updated);
        assertEquals("Existing first name", updated.firstName);
        assertEquals("Existing last name", updated.lastName);
        assertNull(updated.email);
        assertEquals("456", updated.phone);
        assertEquals(1, userDao.updateCount);
        assertSame(existing, userDao.updatedUser);
    }

    @Test
    void updateProfile_withoutRealChangesDoesNotUpdateDao() {
        User existing = user(1);
        existing.firstName = "Existing first name";
        existing.email = null;
        RecordingUserDao userDao = new RecordingUserDao(existing);

        User requested = user(1);
        requested.firstName = "Existing first name";
        requested.email = "";

        User updated = new UserServiceImpl(userDao).updateProfile(requested);

        assertSame(existing, updated);
        assertEquals(0, userDao.updateCount);
        assertNull(userDao.updatedUser);
    }

    @Test
    void updateProfile_unknownUserReturnsNullWithoutUpdate() {
        RecordingUserDao userDao = new RecordingUserDao(null);

        assertNull(new UserServiceImpl(userDao).updateProfile(user(1)));
        assertEquals(0, userDao.updateCount);
    }

    private static User user(int userId) {
        User user = new User();
        user.userId = userId;
        return user;
    }

    private static final class RecordingUserDao implements UserDao {
        private final User existing;
        private int updateCount;
        private User updatedUser;

        private RecordingUserDao(User existing) {
            this.existing = existing;
        }

        @Override
        public User getUser(int userId) {
            return existing;
        }

        @Override
        public List<User> getUsers(Integer organizationId, Integer userId, String firstName, String lastName, String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> getUsersByLocation(int locationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateUser(User user) {
            updateCount++;
            updatedUser = user;
            return true;
        }

        @Override
        public void insertUser(User user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User getUserByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updatePassword(int userId, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteUser(int userId) {
            throw new UnsupportedOperationException();
        }
    }
}
