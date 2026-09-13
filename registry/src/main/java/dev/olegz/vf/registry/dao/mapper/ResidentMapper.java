package dev.olegz.vf.registry.dao.mapper;

import java.util.List;
import dev.olegz.vf.registry.domain.account.Resident;
import org.apache.ibatis.annotations.Param;

public interface ResidentMapper {
    void insertResident(Resident resident);
    Resident selectResident(@Param("organizationId") int organizationId, @Param("residentId") int residentId);
    List<Resident> selectResidents(int organizationId);
    List<Resident> selectResidentsByLocation(int locationId);
    boolean updateResident(Resident resident);
}
