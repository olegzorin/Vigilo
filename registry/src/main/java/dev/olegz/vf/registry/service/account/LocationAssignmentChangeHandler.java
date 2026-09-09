package dev.olegz.vf.registry.service.account;

/** Receives committed-in-transaction location membership changes from the registry. */
public interface LocationAssignmentChangeHandler {
    void locationMembershipChanged(int locationId);
}
