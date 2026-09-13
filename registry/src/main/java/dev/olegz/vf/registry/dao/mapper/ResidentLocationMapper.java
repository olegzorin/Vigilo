package dev.olegz.vf.registry.dao.mapper;

import java.util.List;

import dev.olegz.vf.registry.domain.account.LocationResident;
import org.apache.ibatis.annotations.Param;

public interface ResidentLocationMapper {

    Integer lockResident(int residentId);

    /**
     * Inserts a row only if the resident has no active (end_date NULL or in the future) location
     * assignment.
     * @return the number of rows inserted (0 or 1).
     */
    int insertResidentLocation(LocationResident locationResident);

    LocationResident selectResidentLocation(
        @Param("residentId") int residentId,
        @Param("locationId") int locationId);

    List<LocationResident> selectResidentLocationsByResident(int residentId);

    List<LocationResident> selectResidentLocationsByLocation(int locationId);

    boolean hasActiveAssignments(int locationId);

    boolean deleteResidentLocation(
        @Param("residentId") int residentId,
        @Param("locationId") int locationId);

}
