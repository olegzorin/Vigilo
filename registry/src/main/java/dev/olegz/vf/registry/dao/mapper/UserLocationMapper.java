package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.domain.account.LocationUser;
import org.apache.ibatis.annotations.Param;

public interface UserLocationMapper {

    Integer lockUser(int userId);

    /**
     * Inserts a row only if the user has no active (end_date NULL or in the future) location
     * assignment.
     * @return the number of rows inserted (0 or 1).
     */
    int insertUserLocation(LocationUser locationUser);

    LocationUser selectUserLocation(
        @Param("userId") int userId,
        @Param("locationId") int locationId);

    List<LocationUser> selectUserLocationsByUser(int userId);

    List<LocationUser> selectUserLocationsByLocation(int locationId);

    boolean hasActiveAssignments(int locationId);

    boolean deleteUserLocation(
        @Param("userId") int userId,
        @Param("locationId") int locationId);

}
