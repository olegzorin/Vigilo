package dev.olegz.vf.registry.dao;

import java.util.List;

import dev.olegz.vf.registry.domain.account.LocationResident;

public interface ResidentLocationDao {

    /**
     * Open an assignment of a user to a location. Does nothing if the resident already has any active
     * (not yet ended) location assignment. If {@link LocationResident#startDate} is not set it defaults
     * to the current time.
     * @return {@code true} if the assignment was opened.
     */
    boolean insertResidentLocation(LocationResident locationResident);

    /**
     * @return the active assignment (end_date {@code NULL} or in the future) for the given resident and
     *         location, or {@code null} if the resident has no active assignment to that location.
     */
    LocationResident getResidentLocation(int residentId, int locationId);

    /**
     * @return the active assignments for the given resident, ordered by start date.
     */
    List<LocationResident> getResidentLocationsByResident(int residentId);

    /**
     * @return the active assignments for the given location, ordered by start date.
     */
    List<LocationResident> getResidentLocationsByLocation(int locationId);

    /**
     * @return {@code true} if the location has at least one active assignment.
     */
    boolean hasActiveAssignments(int locationId);

    /**
     * Close the active assignment of the resident to the location by setting its end date to the current
     * time (the row with the given resident_id and location_id whose end_date is {@code NULL} or in the
     * future).
     * @return {@code true} if an active assignment was closed.
     */
    boolean deleteResidentLocation(int residentId, int locationId);

}
