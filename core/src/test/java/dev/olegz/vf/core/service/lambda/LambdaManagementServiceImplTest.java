package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Constructor;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.core.dao.mapper.DevTeamMapper;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.domain.lambdaversion.DevTeam;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the dev-team membership checks in {@link LambdaManagementService}. Both
 * {@code createLambda} (caller must belong to the target team) and {@code updateLambda} (caller must
 * belong to the lambda's own team) reject non-members with {@link ObjectNotFoundException} so that a
 * team/lambda the caller cannot access is indistinguishable from one that does not exist.
 * <p>
 * Each test seeds its own users, dev team and members via the mapper and runs inside a transaction
 * that is rolled back afterwards, so no data leaks between tests or into the database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class LambdaManagementServiceImplTest {

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaManagementService lambdaManagementService;
    private final DevTeamMapper devTeamsMapper;
    private final UserDao userDao;
    private final LambdaDao lambdaDao;
    private final LambdaCodeUploadDao lambdaCodeUploadDao;

    @Autowired
    LambdaManagementServiceImplTest(LambdaManagementService lambdaManagementService,
                                    DevTeamMapper devTeamsMapper, UserDao userDao, LambdaDao lambdaDao,
                                    LambdaCodeUploadDao lambdaCodeUploadDao) {
        this.lambdaManagementService = lambdaManagementService;
        this.devTeamsMapper = devTeamsMapper;
        this.userDao = userDao;
        this.lambdaDao = lambdaDao;
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
    }

    private int insertUser() {
        User user = new User();
        userDao.insertUser(user);
        return user.userId;
    }

    private int insertTeam(String name) {
        DevTeam team = new DevTeam(insertUser(), name, null);
        devTeamsMapper.insertDevTeam(team);
        return team.devTeamId;
    }

    private void addMember(int teamId, int userId, Datetime startDate, Datetime endDate) {
        devTeamsMapper.insertDevTeamMember(teamId, userId, startDate, endDate);
    }

    private int insertLambda(int teamId, String name) {
        Lambda lambda = new Lambda();
        lambda.lambdaName = name;
        lambda.devTeamId = teamId;
        lambda.description = "original";
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        return lambda.lambdaId;
    }

    // --- createLambda -------------------------------------------------------------------------

    @Test
    void createLambda_activeMember_createsLambdaOnTeam() {
        int teamId = insertTeam("Create Team");
        int userId = insertUser();
        addMember(teamId, userId, Datetime.nowMinusDays(1), null);

        int lambdaId = lambdaManagementService.createLambda(userId, "create_ok", teamId, "desc", null);

        assertTrue(lambdaId > 0);
        Lambda stored = lambdaDao.getLambda(lambdaId);
        assertNotNull(stored);
        assertEquals(teamId, stored.devTeamId);
        assertEquals("create_ok", stored.lambdaName);
    }

    @Test
    void createLambda_notMember_throwsObjectNotFound_andPersistsNothing() {
        int teamId = insertTeam("Closed Team");
        int member = insertUser();
        int stranger = insertUser();
        addMember(teamId, member, Datetime.nowMinusDays(1), null);

        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.createLambda(stranger, "create_denied", teamId, "desc", null));

        // The same unique name remains available to an authorized member.
        int lambdaId = lambdaManagementService.createLambda(member, "create_denied", teamId, "desc", null);
        assertTrue(lambdaId > 0);
    }

    @Test
    void createLambda_expiredMembership_throwsObjectNotFound() {
        int teamId = insertTeam("Expired Team");
        int userId = insertUser();
        addMember(teamId, userId, Datetime.nowMinusDays(10), Datetime.nowMinusDays(1));

        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.createLambda(userId, "create_expired", teamId, "desc", null));
    }

    @Test
    void createLambda_unknownTeam_throwsObjectNotFound() {
        int userId = insertUser();

        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.createLambda(userId, "create_no_team", -1, "desc", null));
    }

    // --- updateLambda -------------------------------------------------------------------------

    @Test
    void updateLambda_activeMember_updatesDescription() {
        int teamId = insertTeam("Update Team");
        int userId = insertUser();
        addMember(teamId, userId, Datetime.nowMinusDays(1), null);
        int lambdaId = insertLambda(teamId, "update_ok");

        lambdaManagementService.updateLambda(userId, lambdaId, "changed", null);

        Lambda reloaded = lambdaDao.getLambda(lambdaId);
        assertEquals("changed", reloaded.description);
    }

    @Test
    void updateLambda_notMemberOfLambdaTeam_throwsObjectNotFound_andLeavesLambdaUnchanged() {
        int teamId = insertTeam("Owner Team");
        int owner = insertUser();
        int stranger = insertUser();
        addMember(teamId, owner, Datetime.nowMinusDays(1), null);
        int lambdaId = insertLambda(teamId, "update_denied");

        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.updateLambda(stranger, lambdaId, "hacked", null));

        Lambda reloaded = lambdaDao.getLambda(lambdaId);
        assertEquals("original", reloaded.description);
    }

    @Test
    void updateLambda_unknownLambda_throwsObjectNotFound() {
        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.updateLambda(insertUser(), -1, "desc", null));
    }

    @Test
    void getLambdaCodeUpload_requiresAuthorizedUserAndMatchingLambda() {
        int teamId = insertTeam("Upload Lookup Team");
        int userId = insertUser();
        int strangerId = insertUser();
        addMember(teamId, userId, Datetime.nowMinusDays(1), null);
        int requestedLambdaId = insertLambda(teamId, "upload_lookup_requested");
        int uploadLambdaId = insertLambda(teamId, "upload_lookup_owner");
        LambdaVersion uploadVersion = insertLambdaVersion(uploadLambdaId, userId);
        LambdaCodeUpload upload = insertLambdaCodeUpload(uploadLambdaId, uploadVersion.lambdaVersionId, userId);

        LambdaCodeUpload found = lambdaManagementService.getLambdaCodeUpload(userId, uploadLambdaId, upload.uploadId);
        assertEquals(upload.uploadId, found.uploadId);

        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.getLambdaCodeUpload(strangerId, uploadLambdaId, upload.uploadId));
        assertThrows(ObjectNotFoundException.class,
                () -> lambdaManagementService.getLambdaCodeUpload(userId, requestedLambdaId, upload.uploadId));
    }

    private LambdaVersion insertLambdaVersion(int lambdaId, int userId) {
        LambdaVersion lambdaVersion = new LambdaVersion();
        lambdaVersion.lambdaId = lambdaId;
        lambdaVersion.createdAt = Datetime.now();
        lambdaVersion.statusDate = lambdaVersion.createdAt;
        lambdaVersion.createdBy = userId;
        lambdaVersion.trigger = 0;
        lambdaVersion.version = "0.1.0";
        lambdaVersion.sequenceNumber = 1;
        lambdaVersion.status = LambdaVersionStatus.DRAFT;
        lambdaDao.insertLambdaVersionConfig(lambdaVersion);
        return lambdaVersion;
    }

    private LambdaCodeUpload insertLambdaCodeUpload(int lambdaId, int lambdaVersionId, int userId) {
        LambdaCodeUpload upload = instantiateLambdaCodeUpload();
        upload.lambdaId = lambdaId;
        upload.lambdaVersionId = lambdaVersionId;
        upload.userId = userId;
        upload.status = UploadStatus.CREATED;
        upload.statusDate = Datetime.now();
        upload.codeObjectId = "code/" + lambdaId + "/lookup-test";
        upload.arch = 1;
        upload.stageImage = "python:3.13";
        upload.baseImage = "public.ecr.aws/lambda/python:3.13";
        upload.repoName = "lookup-test-repository";
        lambdaCodeUploadDao.insertLambdaCodeUpload(upload);
        return upload;
    }

    private static LambdaCodeUpload instantiateLambdaCodeUpload() {
        try {
            Constructor<LambdaCodeUpload> constructor = LambdaCodeUpload.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot create LambdaCodeUpload test fixture", e);
        }
    }
}
