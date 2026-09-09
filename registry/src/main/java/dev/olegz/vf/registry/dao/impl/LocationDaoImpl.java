package dev.olegz.vf.registry.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.registry.dao.LocationDao;
import dev.olegz.vf.registry.dao.mapper.LocationMapper;
import dev.olegz.vf.registry.domain.account.Address;
import dev.olegz.vf.registry.domain.account.Location;
import dev.olegz.vf.registry.domain.account.LocationCurrentState;
import dev.olegz.vf.registry.domain.account.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("locationsDao")
public class LocationDaoImpl implements LocationDao {
    private final LocationMapper mapper;

    public LocationDaoImpl(LocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS,
            CacheNames.TRIGGER_LOCATION_METADATA
        },
        key = "#location.locationId")
    public void insertLocation(Location location) {
        if (location.createdAt == null) {
            location.createdAt = Datetime.now();
        }
        if (location.address == null) {
            location.address = new Address();
        }
        mapper.insertLocation(location);
    }

    @Override
    public Location getOrganizationLocation(int organizationId, int locationId) {
        return mapper.selectLocation(organizationId, locationId);
    }

    @Override
    public LocationCurrentState getLocationCurrentState(int organizationId, int locationId) {
        return mapper.selectLocationCurrentState(organizationId, locationId);
    }

    @Override
    @Transactional
    public LocationCurrentState saveLocationCurrentState(LocationCurrentState currentState) {
        if (currentState.stateDate == null) {
            currentState.stateDate = Datetime.now();
        }
        if (mapper.lockLocation(currentState.locationId) == null) {
            throw new ObjectNotFoundException("Location " + currentState.locationId + " not found");
        }
        LocationCurrentState previousState = mapper.selectLocationCurrentStateByLocationId(currentState.locationId);
        if (mapper.updateLocationCurrentState(currentState) == 0) {
            mapper.insertLocationCurrentState(currentState);
        }
        return previousState;
    }

    @Override
    public List<Location> getLocationsByOrganization(int organizationId) {
        return mapper.selectLocationsByOrganization(organizationId);
    }

    @Override
    public Location getLocationByUser(User user) {
        return mapper.selectLocationByUser(user.userId, user.organizationId);
    }

    @Override
    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS,
            CacheNames.TRIGGER_LOCATION_METADATA
        },
        key = "#location.locationId")
    public boolean updateLocation(Location location) {
        if (location.address == null) {
            location.address = new Address();
        }
        return mapper.updateLocation(location);
    }

    @Override
    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS,
            CacheNames.TRIGGER_LOCATION_METADATA,
            CacheNames.TRIGGER_LOCATION_DEVICE_METADATA
        },
        key = "#locationId")
    public boolean deleteLocation(int locationId) {
        return mapper.deleteLocation(locationId);
    }
}
