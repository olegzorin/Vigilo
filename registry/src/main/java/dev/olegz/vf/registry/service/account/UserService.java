package dev.olegz.vf.registry.service.account;

import java.util.List;

import dev.olegz.vf.registry.domain.account.User;

public interface UserService {

    /**
     * Create a new user, hashing the given raw password before it is stored.
     * On return {@link User#userId} holds the generated id and {@link User#password}
     * holds the stored hash.
     */
    void createUser(User user, String rawPassword);

    /**
     * @return the active user with the given id, or {@code null} if none exists or the user has ended.
     */
    User getUser(int userId);

    /**
     * Search active users within an organization (admin access) or by one user id (self access).
     * Exactly one scope must be supplied. Blank filters are treated as absent.
     */
    List<User> getUsers(
        Integer organizationId,
        Integer userId,
        String firstName,
        String lastName,
        String email);

    /**
     * @return the active users currently assigned to the location.
     */
    List<User> getUsersByLocation(int locationId);

    /**
     * Update the profile fields (first/last name, email, phone) of an existing user,
     * taken from the given {@code user}. Identity and credentials — username, password,
     * organization and the start/end dates — are preserved from the stored record; use
     * {@link #changePassword(int, String)} to change the password.
     * @return the updated user, or {@code null} if no active user has {@link User#userId}.
     */
    User updateProfile(User user);

    /**
     * Validate and hash the given raw password, then store it for an active user.
     * @return {@code true} if an active user's password was updated.
     */
    boolean changePassword(int userId, String rawPassword);

    /**
     * Verify a username/password pair.
     * @return the authenticated user.
     */
    User authenticate(String username, String rawPassword);
}
