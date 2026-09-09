package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.registry.dao.mapper.DeviceMapper;
import dev.olegz.vf.core.domain.lambdarun.input.LocationDeviceMetadataSnapshot;
import dev.olegz.vf.core.domain.lambdarun.input.LocationHydrationRow;
import dev.olegz.vf.core.domain.lambdarun.input.LocationMetadataSnapshot;
import dev.olegz.vf.registry.domain.device.LocationDevice;
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
class LambdaRunHydrationDaoTest {
    private static final int SEED_ORGANIZATION_ID = 1;
    private static final int SEED_LOCATION_ID = 1;

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaRunMapper mapper;
    private final DeviceMapper deviceMapper;

    @Autowired
    LambdaRunHydrationDaoTest(LambdaRunMapper mapper, DeviceMapper deviceMapper) {
        this.mapper = mapper;
        this.deviceMapper = deviceMapper;
    }

    @Test
    void triggerLocationMetadataAndLiveHydrationMapFromDatabase() {
        var seedDevices = deviceMapper.selectDevices(SEED_ORGANIZATION_ID, null, null);
        assertFalse(seedDevices.isEmpty());

        LocationDevice assignment = new LocationDevice();
        assignment.deviceUuid = seedDevices.getFirst().deviceUuid;
        assignment.locationId = SEED_LOCATION_ID;
        assignment.startDate = Datetime.now();
        assertEquals(1, deviceMapper.insertLocationDevice(assignment));

        LocationMetadataSnapshot metadata =
            mapper.selectTriggerLocationMetadata(SEED_LOCATION_ID);
        List<LocationDeviceMetadataSnapshot> deviceMetadata =
            mapper.selectTriggerLocationDeviceMetadata(SEED_LOCATION_ID);
        List<LocationHydrationRow> rows = mapper.selectTriggerLocationHydration(
            SEED_LOCATION_ID, new Timestamp(System.currentTimeMillis()));
        assertNotNull(mapper.selectRuntimeAssignmentsForLocation(SEED_LOCATION_ID));

        assertNotNull(metadata);
        assertNotNull(metadata.name);
        assertEquals(1, deviceMetadata.size());
        assertEquals(assignment.deviceUuid, deviceMetadata.getFirst().deviceUuid);
        assertEquals(assignment.locationId, deviceMetadata.getFirst().locationId);
        assertNotNull(deviceMetadata.getFirst().deviceName);
        assertTrue(deviceMetadata.getFirst().startDate > 0);
        assertFalse(rows.isEmpty());
        assertEquals(0, rows.getFirst().rowType);
        assertEquals(assignment.deviceUuid, rows.get(1).deviceUuid);
    }
}
