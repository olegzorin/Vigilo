package dev.olegz.vf.registry.service.account;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.olegz.vf.common.exception.UserExistsException;
import dev.olegz.vf.common.exception.WeakPasswordException;
import dev.olegz.vf.common.exception.WrongUserPasswordException;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.security.PasswordHasher;
import org.springframework.stereotype.Service;

@Service("usersService")
public class UserServiceImpl implements UserService {

    private static final Pattern USER_PASSWORD_PATTERN =
        Pattern.compile(PropertyStore.getString("vf.password.format", "^.{8,}$"));
    private static final Pattern THREE_REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{2}");
    private static final Pattern REPEATED_GROUP_PATTERN = Pattern.compile("(.{3,}?)\\1");

    public static void checkUserPassword(String password) {
        if (password == null || !USER_PASSWORD_PATTERN.matcher(password).matches()
            || THREE_REPEATED_CHARS_PATTERN.matcher(password).find()
            || REPEATED_GROUP_PATTERN.matcher(password).find())
        {
            throw new WeakPasswordException();
        }
    }

    private final UserDao userDao;

    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void createUser(User user, String rawPassword) {
        if (userDao.getUserByUsername(user.username) != null) {
            throw new UserExistsException(user.username, null);
        }
        checkUserPassword(rawPassword);
        user.password = PasswordHasher.passwordHash(rawPassword);
        userDao.insertUser(user);
    }

    @Override
    public User getUser(int userId) {
        return userDao.getUser(userId);
    }

    @Override
    public List<User> getUsers(
        Integer organizationId,
        Integer userId,
        String firstName,
        String lastName,
        String email)
    {
        if ((organizationId == null) == (userId == null)) {
            throw new IllegalArgumentException("Exactly one user search scope is required");
        }
        return userDao.getUsers(
            organizationId,
            userId,
            normalizeFilter(firstName),
            normalizeFilter(lastName),
            normalizeFilter(email));
    }

    @Override
    public List<User> getUsersByLocation(int locationId) {
        return userDao.getUsersByLocation(locationId);
    }

    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public User updateProfile(User user) {
        User existing = userDao.getUser(user.userId);
        if (existing == null) {
            return null;
        }

        boolean changed = false;
        if (isChanged(existing.firstName, user.firstName)) {
            existing.firstName = normalize(user.firstName);
            changed = true;
        }
        if (isChanged(existing.lastName, user.lastName)) {
            existing.lastName = normalize(user.lastName);
            changed = true;
        }
        if (isChanged(existing.email, user.email)) {
            existing.email = normalize(user.email);
            changed = true;
        }
        if (isChanged(existing.phone, user.phone)) {
            existing.phone = normalize(user.phone);
            changed = true;
        }

        if (changed) {
            userDao.updateUser(existing);
        }
        return existing;
    }

    private static boolean isChanged(String existing, String requested) {
        return requested != null && !Objects.equals(existing, normalize(requested));
    }

    private static String normalize(String value) {
        return value.isEmpty() ? null : value;
    }

    @Override
    public boolean changePassword(int userId, String rawPassword) {
        checkUserPassword(rawPassword);
        return userDao.updatePassword(userId, PasswordHasher.passwordHash(rawPassword));
    }

    @Override
    public User authenticate(String username, String rawPassword) {
        User user = userDao.getUserByUsername(username);
        if (user == null || !PasswordHasher.verifyPassword(rawPassword, user.password)) {
            throw new WrongUserPasswordException();
        }
        return user;
    }
}
