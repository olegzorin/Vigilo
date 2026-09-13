package dev.olegz.vf.registry.dao.impl;

import java.util.List;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.dao.ResidentDao;
import dev.olegz.vf.registry.dao.mapper.ResidentMapper;
import dev.olegz.vf.registry.domain.account.Resident;
import org.springframework.stereotype.Repository;

@Repository
public class ResidentDaoImpl implements ResidentDao {
    private final ResidentMapper mapper;
    public ResidentDaoImpl(ResidentMapper mapper) { this.mapper = mapper; }
    public void insertResident(Resident resident) {
        resident.createdAt = Datetime.now();
        mapper.insertResident(resident);
    }
    public Resident getResident(int organizationId, int residentId) { return mapper.selectResident(organizationId, residentId); }
    public List<Resident> getResidents(int organizationId) { return mapper.selectResidents(organizationId); }
    public List<Resident> getResidentsByLocation(int locationId) { return mapper.selectResidentsByLocation(locationId); }
    public boolean updateResident(Resident resident) { return mapper.updateResident(resident); }
}
