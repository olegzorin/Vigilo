package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.domain.account.LocationUser;

public interface UserLocationDao {

    /**
     * Open an assignment of a user to a location. Does nothing if the user already has any active
     * (not yet ended) location assignment. If {@link LocationUser#startDate} is not set it defaults
     * to the current time.
     * @return {@code true} if the assignment was opened.
     */
    boolean insertUserLocation(LocationUser locationUser);

    /**
     * @return the active assignment (end_date {@code NULL} or in the future) for the given user and
     *         location, or {@code null} if the user has no active assignment to that location.
     */
    LocationUser getUserLocation(int userId, int locationId);

    /**
     * @return the active assignments for the given user, ordered by start date.
     */
    List<LocationUser> getUserLocationsByUser(int userId);

    /**
     * @return the active assignments for the given location, ordered by start date.
     */
    List<LocationUser> getUserLocationsByLocation(int locationId);

    /**
     * @return {@code true} if the location has at least one active assignment.
     */
    boolean hasActiveAssignments(int locationId);

    /**
     * Close the active assignment of the user to the location by setting its end date to the current
     * time (the row with the given user_id and location_id whose end_date is {@code NULL} or in the
     * future).
     * @return {@code true} if an active assignment was closed.
     */
    boolean deleteUserLocation(int userId, int locationId);

}
