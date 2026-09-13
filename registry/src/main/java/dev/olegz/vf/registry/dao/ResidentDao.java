package dev.olegz.vf.registry.dao;

import java.util.List;
import dev.olegz.vf.registry.domain.account.Resident;

public interface ResidentDao {
    void insertResident(Resident resident);
    Resident getResident(int organizationId, int residentId);
    List<Resident> getResidents(int organizationId);
    List<Resident> getResidentsByLocation(int locationId);
    boolean updateResident(Resident resident);
}
