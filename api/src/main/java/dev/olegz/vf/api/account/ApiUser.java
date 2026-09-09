package dev.olegz.vf.api.account;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.domain.account.User;

/**
 * API view of a {@link User}. Deliberately omits the password hash so it is never exposed
 * over the wire.
 */
public class ApiUser {
    public final int userId;
    public final String username;
    public final String firstName;
    public final String lastName;
    public final String email;
    public final String phone;
    public final int organizationId;
    public final Datetime createdAt;
    public final Datetime deletedAt;

    public ApiUser(User user) {
        this.userId = user.userId;
        this.username = user.username;
        this.firstName = user.firstName;
        this.lastName = user.lastName;
        this.email = user.email;
        this.phone = user.phone;
        this.organizationId = user.organizationId;
        this.createdAt = user.createdAt;
        this.deletedAt = user.deletedAt;
    }
}
