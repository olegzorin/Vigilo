package dev.olegz.vf.registry.domain.account;

import dev.olegz.vf.common.Datetime;

/** A monitored person, with no login credentials or application permissions. */
public class Resident {
    public int residentId;
    public int organizationId;
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public boolean synthetic;
    public Datetime createdAt;
    public Datetime deletedAt;
}
