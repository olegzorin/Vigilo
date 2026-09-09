package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.registry.dao.UserLocationDao;
import dev.olegz.vf.registry.dao.mapper.UserLocationMapper;
import dev.olegz.vf.registry.domain.account.LocationUser;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("userLocationsDao")
public class UserLocationDaoImpl implements UserLocationDao {
    private final UserLocationMapper mapper;

    public UserLocationDaoImpl(UserLocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean insertUserLocation(LocationUser locationUser) {
        if (locationUser.startDate == null) {
            locationUser.startDate = Datetime.now();
        }
        // Serialize assignments for the same user so concurrent requests cannot both pass the
        // NOT EXISTS check and create two active assignments.
        mapper.lockUser(locationUser.userId);
        return mapper.insertUserLocation(locationUser) == 1;
    }

    @Override
    public LocationUser getUserLocation(int userId, int locationId) {
        return mapper.selectUserLocation(userId, locationId);
    }

    @Override
    public List<LocationUser> getUserLocationsByUser(int userId) {
        return mapper.selectUserLocationsByUser(userId);
    }

    @Override
    public List<LocationUser> getUserLocationsByLocation(int locationId) {
        return mapper.selectUserLocationsByLocation(locationId);
    }

    @Override
    public boolean hasActiveAssignments(int locationId) {
        return mapper.hasActiveAssignments(locationId);
    }

    @Override
    public boolean deleteUserLocation(int userId, int locationId) {
        return mapper.deleteUserLocation(userId, locationId);
    }

}
