package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.dao.ResidentLocationDao;
import dev.olegz.vf.registry.dao.mapper.ResidentLocationMapper;
import dev.olegz.vf.registry.domain.account.LocationResident;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("residentLocationsDao")
public class ResidentLocationDaoImpl implements ResidentLocationDao {
    private final ResidentLocationMapper mapper;

    public ResidentLocationDaoImpl(ResidentLocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean insertResidentLocation(LocationResident locationResident) {
        if (locationResident.startDate == null) {
            locationResident.startDate = Datetime.now();
        }
        // Serialize assignments for the same resident so concurrent requests cannot both pass the
        // NOT EXISTS check and create two active assignments.
        mapper.lockResident(locationResident.residentId);
        return mapper.insertResidentLocation(locationResident) == 1;
    }

    @Override
    public LocationResident getResidentLocation(int residentId, int locationId) {
        return mapper.selectResidentLocation(residentId, locationId);
    }

    @Override
    public List<LocationResident> getResidentLocationsByResident(int residentId) {
        return mapper.selectResidentLocationsByResident(residentId);
    }

    @Override
    public List<LocationResident> getResidentLocationsByLocation(int locationId) {
        return mapper.selectResidentLocationsByLocation(locationId);
    }

    @Override
    public boolean hasActiveAssignments(int locationId) {
        return mapper.hasActiveAssignments(locationId);
    }

    @Override
    public boolean deleteResidentLocation(int residentId, int locationId) {
        return mapper.deleteResidentLocation(residentId, locationId);
    }

}
