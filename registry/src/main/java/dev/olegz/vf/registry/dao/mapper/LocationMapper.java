package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import org.apache.ibatis.annotations.Param;

public interface LocationMapper {

    void insertLocation(Location location);

    Location selectLocation(
        @Param("organizationId") int organizationId,
        @Param("locationId") int locationId);

    LocationCurrentState selectLocationCurrentState(
        @Param("organizationId") int organizationId,
        @Param("locationId") int locationId);

    LocationCurrentState selectLocationCurrentStateByLocationId(int locationId);

    Integer lockLocation(int locationId);

    int insertLocationCurrentState(LocationCurrentState currentState);

    int updateLocationCurrentState(LocationCurrentState currentState);

    List<Location> selectLocationsByOrganization(int organizationId);

    Location selectLocationByUser(
        @Param("userId") int userId,
        @Param("organizationId") int organizationId);

    boolean updateLocation(Location location);

    boolean deleteLocation(int locationId);

}
