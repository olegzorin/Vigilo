package dev.olegz.vf.core.service.lambda;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.domain.lambdaversion.VersionBump;
import dev.olegz.vf.core.event.TriggerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage for {@link LambdaManagementService#setLambdaConfiguration}, the "save lambda
 * configuration" path. It drives {@code LambdasDao.insertLambdaVersionConfig}, which writes both the
 * {@code lambda_versions} history row and the {@code lambda_active_versions} row (via
 * {@code insertLambdaVersionActive}). That active insert had a corrupted VALUES
 * clause that made every save fail at runtime; it went unnoticed because no test drove this method
 * end-to-end (the sibling {@code LambdaManagementServiceImplTest} covers only the dev-team membership
 * checks in {@code createLambda}/{@code updateLambda} and never reaches the save path).
 * <p>
 * {@code setLambdaConfiguration} runs with {@code REQUIRES_NEW}, so it commits in its own transaction and
 * a test-level {@code @Rollback} cannot undo it. This test therefore commits real rows against the
 * seeded developer/team (ids 1) and deletes the created lambda in {@link #tearDown()}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
class SetLambdaConfigurationIntegrationTest {

    private static final int SEED_USER_ID = 1;   // seeded admin@demo.org
    private static final int SEED_TEAM_ID = 1;   // seeded "Demo Dev Team"
    private static final String LAMBDA_NAME = "test_setlambdaconfiguration_lambda";

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaManagementService service;
    private final LambdaDao lambdaDao;

    @Autowired
    SetLambdaConfigurationIntegrationTest(LambdaManagementService service, LambdaDao lambdaDao) {
        this.service = service;
        this.lambdaDao = lambdaDao;
    }

    @AfterEach
    void tearDown() {
        // setLambdaConfiguration commits (REQUIRES_NEW), so clean up the committed rows explicitly.
        lambdaDao.deleteLambda(LAMBDA_NAME, SEED_USER_ID, SEED_TEAM_ID);
    }

    private int insertLambda() {
        Lambda lambda = new Lambda();
        lambda.lambdaName = LAMBDA_NAME;
        lambda.devTeamId = SEED_TEAM_ID;
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        return lambda.lambdaId;
    }

    /**
     * A minimal configuration that passes {@link LambdaConfig#checkTriggers()} with a location-event trigger.
     */
    private static LambdaConfig locationTriggeredConfig(VersionBump bump) {
        LambdaConfig config = new LambdaConfig();
        config.bump = bump;
        config.trigger = TriggerEvent.TRIGGER_LOCATION_EVENT;
        return config;
    }

    @Test
    void setLambdaConfiguration_savesActiveAndHistory_andReSaveIsIdempotent() {
        int lambdaId = insertLambda();

        // First save: a brand-new lambda has no published version, so the number derives from a null
        // high-water mark (MINOR of 0.0.0 -> 0.1.0). Drives insertLambdaVersionActive's INSERT branch.
        LambdaVersion first = service.setLambdaConfiguration(SEED_USER_ID, lambdaId, locationTriggeredConfig(VersionBump.MINOR));
        assertTrue(first.lambdaVersionId > 0);
        assertEquals("0.1.0", first.version);
        assertEquals(1, first.sequenceNumber);               // first created version gets internal sequence 1
        assertEquals(LambdaVersionStatus.DRAFT, first.status);

        // The dev version and its active row were committed and read back through the active-version join.
        List<LambdaVersion> afterFirst = lambdaDao.getLambdaVersions(lambdaId, null, null);
        assertNotNull(afterFirst);
        assertEquals(1, afterFirst.size());
        LambdaVersion persisted = afterFirst.get(0);
        assertEquals(first.lambdaVersionId, persisted.lambdaVersionId);
        assertEquals("0.1.0", persisted.version);
        assertEquals(1, persisted.sequenceNumber);           // and it was persisted / read back
        assertFalse(persisted.published);                    // a dev version is never public
        assertEquals(LambdaVersion.LATEST_NONE, persisted.latest);
        // Re-saving reuses the same dev version: no publish happened, so max_version is still null and
        // the recomputed number is unchanged. Drives insertLambdaVersionActive's ON CONFLICT UPDATE branch.
        LambdaVersion second = service.setLambdaConfiguration(SEED_USER_ID, lambdaId, locationTriggeredConfig(VersionBump.MINOR));
        assertEquals(first.lambdaVersionId, second.lambdaVersionId);
        assertEquals("0.1.0", second.version);
        assertEquals(1, second.sequenceNumber);              // reusing the dev version does not advance the sequence

        List<LambdaVersion> afterSecond = lambdaDao.getLambdaVersions(lambdaId, null, null);
        assertEquals(1, afterSecond.size());
    }

}
