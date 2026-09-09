package dev.olegz.vf.registry.dao;

import java.util.Map;
import java.util.UUID;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.registry.TestConfiguration;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.device.Device;
import dev.olegz.vf.registry.domain.device.DeviceCurrentState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class DeviceDaoTest {
    private final DeviceDao deviceDao;
    private final OrganizationDao organizationDao;

    @Autowired
    DeviceDaoTest(DeviceDao deviceDao, OrganizationDao organizationDao) {
        this.deviceDao = deviceDao;
        this.organizationDao = organizationDao;
    }

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    @Test
    void saveThenGetCurrentState_insertsAndReturnsPreviousStateOnUpdate() {
        int organizationId = insertOrganization();
        int otherOrganizationId = insertOrganization();
        Device device = insertDevice(organizationId);
        DeviceCurrentState currentState = currentState(
            device.deviceUuid, Map.of("status", "online"), Datetime.now(), Datetime.now());

        DeviceCurrentState previousState = deviceDao.saveDeviceCurrentState(currentState);

        assertNull(previousState);
        DeviceCurrentState inserted =
            deviceDao.getDeviceCurrentState(organizationId, device.deviceUuid);
        assertStateEquals(currentState, inserted);
        assertNull(deviceDao.getDeviceCurrentState(otherOrganizationId, device.deviceUuid));

        DeviceCurrentState updatedState = currentState(
            device.deviceUuid, Map.of("status", "offline"), Datetime.now(), Datetime.now());
        previousState = deviceDao.saveDeviceCurrentState(updatedState);

        assertStateEquals(inserted, previousState);
        assertStateEquals(
            updatedState, deviceDao.getDeviceCurrentState(organizationId, device.deviceUuid));
    }

    private int insertOrganization() {
        Organization organization = new Organization();
        organization.organizationName = "Device DAO test " + System.nanoTime();
        organizationDao.insertOrganization(organization);
        return organization.organizationId;
    }

    private Device insertDevice(int organizationId) {
        Device device = new Device();
        device.deviceUuid = UUID.randomUUID().toString();
        device.organizationId = organizationId;
        device.typeId = 1;
        device.deviceName = "Test device";
        deviceDao.insertDevice(device);
        return device;
    }

    private static DeviceCurrentState currentState(
        String deviceUuid,
        Map<String, Object> state,
        Datetime measuredAt,
        Datetime receivedAt)
    {
        DeviceCurrentState currentState = new DeviceCurrentState();
        currentState.deviceUuid = deviceUuid;
        currentState.state = state;
        currentState.measuredAt = measuredAt;
        currentState.receivedAt = receivedAt;
        return currentState;
    }

    private static void assertStateEquals(
        DeviceCurrentState expected,
        DeviceCurrentState actual)
    {
        assertNotNull(actual);
        assertEquals(expected.deviceUuid, actual.deviceUuid);
        assertEquals(expected.state, actual.state);
        assertEquals(expected.measuredAt, actual.measuredAt);
        assertEquals(expected.receivedAt, actual.receivedAt);
    }
}
