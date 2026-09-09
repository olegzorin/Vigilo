package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.domain.account.User;

public interface UserDao {

    /**
     * Insert a new user. On return {@link User#userId} holds the generated id.
     * If {@link User#createdAt} is not set it defaults to the current time.
     */
    void insertUser(User user);

    /**
     * @return the user with the given id, or {@code null} if none exists or it was deleted.
     */
    User getUser(int userId);

    /**
     * Search non-deleted users within exactly one caller-derived scope. Text filters are optional,
     * case-insensitive substrings. Administrators pass an organization id; ordinary users pass
     * their own user id.
     */
    List<User> getUsers(
        Integer organizationId,
        Integer userId,
        String firstName,
        String lastName,
        String email);

    /**
     * @return the non-deleted users currently assigned to the location, ordered by name and id.
     */
    List<User> getUsersByLocation(int locationId);

    /**
     * @return the non-deleted user with the given username, or {@code null} if none exists.
     */
    User getUserByUsername(String username);

    /**
     * Update the mutable profile fields of a non-deleted user. Does not touch the password
     * or creation timestamp;
     * use {@link #updatePassword(int, String)} for that.
     * @return {@code true} if a row was updated.
     */
    boolean updateUser(User user);

    /**
     * Store the given (already hashed) password for a non-deleted user.
     * @return {@code true} if a non-deleted user's password was updated.
     */
    boolean updatePassword(int userId, String password);

    /**
     * Permanently mark a user deleted by setting its deletion timestamp.
     * @return {@code true} if a non-deleted user was deleted.
     */
    boolean deleteUser(int userId);

}
