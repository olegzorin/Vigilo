package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

public class User {
    public AccountType accountType = AccountType.DEVELOPER;
    public int userId;
    public String username;
    public String password;
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public Datetime createdAt;
    public Datetime deletedAt;
    public int organizationId;
}
