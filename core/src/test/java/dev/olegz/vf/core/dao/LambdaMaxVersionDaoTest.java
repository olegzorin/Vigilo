package dev.olegz.vf.core.dao;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.VersionBump;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the {@code lambdas.max_version} high-water mark that backs automatic version numbering: it is
 * {@code null} for a new lambda, and {@link LambdaDao#updateLambdaMaxVersion} persists a value that
 * round-trips through the lambda read mappers. The next version number is {@code bump.next(maxVersion)}
 * — the arithmetic itself is covered by {@code VersionBumpTest}, and the publish-time advance of the
 * mark lives in {@code LambdaManagementServiceImpl.promoteTestVersionToProduction}.
 * <p>
 * Uses the seeded developer and dev team (ids 1) for the lambda's foreign keys and runs inside a
 * transaction that is rolled back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class LambdaMaxVersionDaoTest {

    private static final int SEED_USER_ID = 1;   // seeded admin@demo.org
    private static final int SEED_TEAM_ID = 1;   // seeded "Demo Dev Team"

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaDao lambdaDao;

    @Autowired
    LambdaMaxVersionDaoTest(LambdaDao lambdaDao) {
        this.lambdaDao = lambdaDao;
    }

    private int insertLambda(String name) {
        Lambda lambda = new Lambda();
        lambda.lambdaName = name;
        lambda.devTeamId = SEED_TEAM_ID;
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        return lambda.lambdaId;
    }

    @Test
    void maxVersion_defaultsToNull_persists_andRoundTripsThroughReadMappers() {
        int lambdaId = insertLambda("test_maxversion_lambda");

        // A new lambda has no high-water mark, so the first version starts from a 0.0.0 basis.
        assertNull(lambdaDao.getLambda(lambdaId).maxVersion);

        lambdaDao.updateLambdaMaxVersion(lambdaId, "2.3.0");

        // The mark round-trips through every lambda read path.
        assertEquals("2.3.0", lambdaDao.getLambda(lambdaId).maxVersion);                   // selectLambdaById / lambdaEntityMap
        assertEquals("2.3.0", lambdaDao.getLambdaForUpdate(lambdaId).maxVersion);          // selectLambdaByIdForUpdate / lambdaMap

        // The stored mark is the basis for the next number.
        assertEquals("2.4.0", VersionBump.MINOR.next(lambdaDao.getLambda(lambdaId).maxVersion));
    }
}
