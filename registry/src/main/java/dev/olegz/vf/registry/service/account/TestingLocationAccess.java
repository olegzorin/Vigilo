package dev.olegz.vf.registry.service.account;

/** Implemented by modules that grant developers access to testing locations. */
public interface TestingLocationAccess {
    boolean hasAccess(int userId, int locationId);
}
