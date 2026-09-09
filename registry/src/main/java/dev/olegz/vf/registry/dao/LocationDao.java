package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.User;

public interface LocationDao {

    /**
     * Insert a new location. On return {@link Location#locationId} holds the generated id.
     * If {@link Location#createdAt} is not set it defaults to the current time.
     */
    void insertLocation(Location location);

    /**
     * @return the location with the given id, or {@code null} if none exists or it was deleted.
     */
    Location getOrganizationLocation(int organizationId, int locationId);

    /**
     * @return the current state for a location in the organization, or {@code null} if none exists.
     */
    LocationCurrentState getLocationCurrentState(int organizationId, int locationId);

    /**
     * Insert or update the current state for a location.
     * If {@link LocationCurrentState#stateDate} is not set it defaults to the current time.
     *
     * @return the state that existed before the save, or {@code null} if this is the first state.
     */
    LocationCurrentState saveLocationCurrentState(LocationCurrentState currentState);

    /**
     * @return the non-deleted locations in the organization, ordered by name and id.
     */
    List<Location> getLocationsByOrganization(int organizationId);

    /**
     * @return the active location currently assigned to the user in the organization, or
     *         {@code null} if the user has no active assignment.
     */
    Location getLocationByUser(User user);

    /**
     * Update the mutable fields of a non-deleted location.
     * @return {@code true} if a row was updated.
     */
    boolean updateLocation(Location location);

    /**
     * Permanently mark a location deleted by setting its deletion timestamp.
     * @return {@code true} if a non-deleted location was deleted.
     */
    boolean deleteLocation(int locationId);

}
