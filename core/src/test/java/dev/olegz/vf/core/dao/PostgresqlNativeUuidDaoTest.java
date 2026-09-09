package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.mapper.LambdaAlertMapper;
import dev.olegz.vf.core.dao.mapper.CronJobLockMapper;
import dev.olegz.vf.core.domain.alert.LambdaAlert;
import dev.olegz.vf.core.domain.alert.LambdaAlertSeverity;
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
class PostgresqlNativeUuidDaoTest {
    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaAlertMapper alertMapper;
    private final CronJobLockMapper lockMapper;

    @Autowired
    PostgresqlNativeUuidDaoTest(LambdaAlertMapper alertMapper, CronJobLockMapper lockMapper) {
        this.alertMapper = alertMapper;
        this.lockMapper = lockMapper;
    }

    @Test
    void uuidAndRawJsonStringsRoundTripThroughPostgresqlTypes() {
        String alertId = UUID.randomUUID().toString();
        LambdaAlert alert = alert(alertId);
        assertEquals(1, alertMapper.insertAlert(alert));

        LambdaAlert stored = alertMapper.selectAlert(alert.idempotencyKey, alert.lambdaAssignmentId);
        assertNotNull(stored);
        assertEquals(alertId, stored.alertId);
        assertEquals("[{\"field\": \"state\"}]", stored.changesJson);

        String ownerId = UUID.randomUUID().toString();
        String jobName = "native-types-" + UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp later = Timestamp.from(Instant.now().plusSeconds(60));
        lockMapper.insert(jobName, ownerId, later);
        assertTrue(lockMapper.renew(jobName, ownerId, later));
        assertTrue(lockMapper.release(jobName, ownerId));
    }

    private static LambdaAlert alert(String alertId) {
        LambdaAlert alert = new LambdaAlert();
        alert.alertId = alertId;
        alert.idempotencyKey = "native-types-" + UUID.randomUUID();
        alert.payloadHash = "a".repeat(64);
        alert.locationId = 1;
        alert.alertType = "TEST";
        alert.severity = LambdaAlertSeverity.WARNING;
        alert.status = "OPEN";
        alert.ruleId = "native-types";
        alert.occurredAt = Timestamp.from(Instant.now());
        alert.lambdaAssignmentId = 1;
        alert.lambdaId = 1;
        alert.lambdaVersionId = 1;
        alert.runId = 1;
        alert.eventKey = "event";
        alert.changesJson = "[{\"field\":\"state\"}]";
        return alert;
    }
}
