package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.domain.lambdarun.input.*;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerEventDataServiceImplTest {

    @Test
    void hydrateResetBuildsResetInputFromLocationSnapshot() {
        ResetEvent event = new ResetEvent(17, 101);
        event.time = 123_000L;
        TriggerEventDataService service = new TriggerEventDataServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) -> switch (method.getName()) {
                case "getTriggerLocationMetadata" -> new LocationMetadataSnapshot();
                case "getTriggerLocationDeviceMetadata" -> List.of();
                case "getTriggerLocationHydration" ->
                    new LocationHydrationSnapshot(null, List.of(), List.of());
                default -> null;
            }));

        TriggerEventData data = service.hydrate(event);

        assertEquals(event.time, data.time);
        assertEquals(ResetEvent.TRIGGER, data.trigger);
        assertEquals(event.locationId, data.locationId);
        assertNotNull(data.location);
    }

    @Test
    void hydrateScheduledEventPreservesFiredScheduleIds() {
        ScheduledEvent event = new ScheduledEvent(17, 101, List.of("morning", "medication"));
        event.time = 123_000L;
        TriggerEventDataService service = new TriggerEventDataServiceImpl(
            proxy(LambdaRunDao.class, (proxy, method, args) -> switch (method.getName()) {
                case "getTriggerLocationMetadata" -> new LocationMetadataSnapshot();
                case "getTriggerLocationDeviceMetadata" -> List.of();
                case "getTriggerLocationHydration" ->
                    new LocationHydrationSnapshot(null, List.of(), List.of());
                default -> null;
            }));

        TriggerEventData data = service.hydrate(event);

        assertEquals(event.time, data.time);
        assertEquals(TriggerEvent.TRIGGER_SCHEDULE, data.trigger);
        assertEquals(event.locationId, data.locationId);
        assertEquals(event.scheduleIds, data.scheduleIds);
        assertThrows(UnsupportedOperationException.class, () -> data.scheduleIds.clear());
    }

    @Test
    void hydrateCombinesEventAndLocationSnapshot() {
        TriggerEvent event = new TriggerEvent(TriggerEvent.TRIGGER_DEVICE_EVENT, 17);
        event.time = 123_000L;
        event.newLocationState = "away";
        event.deviceUuid = "device-1";
        event.newDeviceState = Map.of("alarm", true);

        LocationSnapshot location = new LocationSnapshot();
        location.name = "Home";
        location.currentState = "home";

        LocationUserSnapshot user = new LocationUserSnapshot();
        user.userId = 101;

        TriggerEventData data = TriggerEventDataServiceImpl.hydrate(
            event,
            location,
            List.of(locationDeviceSnapshot()),
            List.of(user));

        assertNotNull(data.key);
        assertTrue(!data.key.isBlank());
        assertEquals(event.time, data.time);
        assertEquals(event.trigger, data.trigger);
        assertEquals(event.locationId, data.locationId);
        assertEquals(event.newLocationState, data.newLocationState);
        assertEquals(event.deviceUuid, data.deviceUuid);
        assertEquals(event.newDeviceState, data.newDeviceState);
        assertEquals(location, data.location);
        assertEquals(1, data.locationDevices.size());
        assertEquals("device-1", data.locationDevices.getFirst().deviceUuid);
        assertEquals(Map.of("online", true), data.locationDevices.getFirst().currentState);
        assertEquals(List.of(user), data.locationUsers);
        assertEquals(List.of(), data.scheduleIds);
        assertThrows(UnsupportedOperationException.class, () -> data.newDeviceState.clear());
        assertThrows(UnsupportedOperationException.class, () -> data.locationDevices.clear());
        assertThrows(UnsupportedOperationException.class, () -> data.locationUsers.clear());
    }

    @Test
    void hydrateUsesEmptyListsWhenLocationHasNoResources() {
        TriggerEventData data = TriggerEventDataServiceImpl.hydrate(
            new TriggerEvent(TriggerEvent.TRIGGER_LOCATION_EVENT, 17),
            null,
            null,
            null);

        assertTrue(data.locationDevices.isEmpty());
        assertTrue(data.locationUsers.isEmpty());
        assertTrue(data.scheduleIds.isEmpty());
    }

    @Test
    void hydrateDevicesCombinesCachedMetadataWithLiveState() {
        LocationDeviceMetadataSnapshot metadata = new LocationDeviceMetadataSnapshot();
        metadata.deviceUuid = "device-1";
        metadata.deviceTypeId = 42;
        metadata.deviceName = "Kitchen sensor";
        metadata.locationId = 17;
        metadata.startDate = 100_000L;

        List<LocationDeviceSnapshot> devices = TriggerEventDataServiceImpl.hydrateDevices(
            List.of(metadata),
            List.of(new LocationDeviceStateSnapshot("device-1", Map.of("online", true))));

        assertEquals(1, devices.size());
        assertEquals(metadata.deviceUuid, devices.getFirst().deviceUuid);
        assertEquals(metadata.deviceTypeId, devices.getFirst().deviceTypeId);
        assertEquals(metadata.deviceName, devices.getFirst().deviceName);
        assertEquals(metadata.locationId, devices.getFirst().locationId);
        assertEquals(metadata.startDate, devices.getFirst().startDate);
        assertEquals(Map.of("online", true), devices.getFirst().currentState);
        assertThrows(UnsupportedOperationException.class, () -> devices.clear());
    }

    private static LocationDeviceSnapshot locationDeviceSnapshot() {
        LocationDeviceSnapshot snapshot = new LocationDeviceSnapshot();
        snapshot.deviceUuid = "device-1";
        snapshot.deviceTypeId = 42;
        snapshot.deviceName = "Kitchen sensor";
        snapshot.locationId = 17;
        snapshot.startDate = 100_000L;
        snapshot.currentState = Map.of("online", true);
        return snapshot;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            handler));
    }
}
