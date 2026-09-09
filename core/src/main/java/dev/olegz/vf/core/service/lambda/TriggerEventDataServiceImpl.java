package dev.olegz.vf.core.service.lambda;

import java.util.*;

import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.domain.lambdarun.input.*;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriggerEventDataServiceImpl implements TriggerEventDataService {
    private final LambdaRunDao lambdaRunDao;

    public TriggerEventDataServiceImpl(LambdaRunDao lambdaRunDao) {
        this.lambdaRunDao = lambdaRunDao;
    }

    @Override
    @Transactional(readOnly = true)
    public TriggerEventData hydrate(ResetEvent event) {
        LocationMetadataSnapshot metadata = lambdaRunDao.getTriggerLocationMetadata(event.locationId);
        List<LocationDeviceMetadataSnapshot> deviceMetadata =
            lambdaRunDao.getTriggerLocationDeviceMetadata(event.locationId);
        LocationHydrationSnapshot hydration = lambdaRunDao.getTriggerLocationHydration(event.locationId);
        return hydrate(
            event.time,
            ResetEvent.TRIGGER,
            event.locationId,
            null,
            null,
            null,
            List.of(),
            LocationSnapshot.from(metadata, hydration.currentState()),
            hydrateDevices(deviceMetadata, hydration.deviceStates()),
            hydration.users());
    }

    @Override
    @Transactional(readOnly = true)
    public TriggerEventData hydrate(ScheduledEvent event) {
        LocationMetadataSnapshot metadata = lambdaRunDao.getTriggerLocationMetadata(event.locationId);
        List<LocationDeviceMetadataSnapshot> deviceMetadata =
            lambdaRunDao.getTriggerLocationDeviceMetadata(event.locationId);
        LocationHydrationSnapshot hydration = lambdaRunDao.getTriggerLocationHydration(event.locationId);
        return hydrate(
            event.time,
            TriggerEvent.TRIGGER_SCHEDULE,
            event.locationId,
            null,
            null,
            null,
            event.scheduleIds,
            LocationSnapshot.from(metadata, hydration.currentState()),
            hydrateDevices(deviceMetadata, hydration.deviceStates()),
            hydration.users());
    }

    @Override
    @Transactional(readOnly = true)
    public TriggerEventData hydrate(TriggerEvent event) {
        LocationMetadataSnapshot metadata = lambdaRunDao.getTriggerLocationMetadata(event.locationId);
        List<LocationDeviceMetadataSnapshot> deviceMetadata =
            lambdaRunDao.getTriggerLocationDeviceMetadata(event.locationId);
        LocationHydrationSnapshot hydration = lambdaRunDao.getTriggerLocationHydration(event.locationId);
        return hydrate(
            event.time,
            event.trigger,
            event.locationId,
            event.newLocationState,
            event.deviceUuid,
            event.newDeviceState,
            List.of(),
            LocationSnapshot.from(metadata, hydration.currentState()),
            hydrateDevices(deviceMetadata, hydration.deviceStates()),
            hydration.users());
    }

    static TriggerEventData hydrate(
        TriggerEvent event,
        LocationSnapshot location,
        List<LocationDeviceSnapshot> devices,
        List<LocationUserSnapshot> users)
    {
        return hydrate(
            event.time,
            event.trigger,
            event.locationId,
            event.newLocationState,
            event.deviceUuid,
            event.newDeviceState,
            List.of(),
            location,
            devices,
            users);
    }

    private static TriggerEventData hydrate(
        long time,
        int trigger,
        int locationId,
        String newLocationState,
        String deviceUuid,
        Map<String, Object> newDeviceState,
        List<String> scheduleIds,
        LocationSnapshot location,
        List<LocationDeviceSnapshot> devices,
        List<LocationUserSnapshot> users)
    {
        TriggerEventData data = new TriggerEventData();
        data.key = UUID.randomUUID().toString();
        data.time = time;
        data.trigger = trigger;
        data.locationId = locationId;
        data.newLocationState = newLocationState;
        data.deviceUuid = deviceUuid;
        data.newDeviceState = newDeviceState != null ?
            Collections.unmodifiableMap(new LinkedHashMap<>(newDeviceState)) : null;
        data.location = location;
        data.locationDevices = devices != null ? List.copyOf(devices) : List.of();
        data.locationUsers = users != null ? List.copyOf(users) : List.of();
        data.scheduleIds = scheduleIds != null ? List.copyOf(scheduleIds) : List.of();
        return data;
    }

    static List<LocationDeviceSnapshot> hydrateDevices(
        List<LocationDeviceMetadataSnapshot> metadata,
        List<LocationDeviceStateSnapshot> states)
    {
        if ((metadata == null) || metadata.isEmpty()) return List.of();

        Map<String, Map<String, Object>> statesByDevice = new LinkedHashMap<>();
        if (states != null) {
            for (LocationDeviceStateSnapshot state : states) {
                if ((state != null) && (state.deviceUuid() != null)) {
                    statesByDevice.put(state.deviceUuid(), state.currentState());
                }
            }
        }

        List<LocationDeviceSnapshot> devices = new ArrayList<>(metadata.size());
        for (LocationDeviceMetadataSnapshot item : metadata) {
            if ((item == null) || (item.deviceUuid == null)) continue;

            LocationDeviceSnapshot device = new LocationDeviceSnapshot();
            device.deviceUuid = item.deviceUuid;
            device.deviceTypeId = item.deviceTypeId;
            device.deviceName = item.deviceName;
            device.locationId = item.locationId;
            device.startDate = item.startDate;
            device.currentState = statesByDevice.get(item.deviceUuid);
            devices.add(device);
        }
        return List.copyOf(devices);
    }
}
