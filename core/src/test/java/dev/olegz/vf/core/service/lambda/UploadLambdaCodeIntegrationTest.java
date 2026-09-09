package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Constructor;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.dao.LambdaDao;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
class UploadLambdaCodeIntegrationTest {

    private static final int SEED_USER_ID = 1;
    private static final int SEED_TEAM_ID = 1;
    private static final String LAMBDA_NAME = "test_uploadlambdacode_lambda";

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaManagementService service;
    private final LambdaWorkerService workerService;
    private final LambdaDao lambdaDao;
    private final LambdaCodeUploadDao lambdaCodeUploadDao;

    @Autowired
    UploadLambdaCodeIntegrationTest(LambdaManagementService service, LambdaWorkerService workerService,
                                 LambdaDao lambdaDao, LambdaCodeUploadDao lambdaCodeUploadDao) {
        this.service = service;
        this.workerService = workerService;
        this.lambdaDao = lambdaDao;
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
    }

    @AfterEach
    void tearDown() {
        lambdaDao.deleteLambda(LAMBDA_NAME, SEED_USER_ID, SEED_TEAM_ID);
    }

    @Test
    void uploadLambdaCode_andSuccessfulDeployment_updateDatabase() {
        Lambda lambda = insertConfiguredLambda();

        LambdaCodeUpload upload = service.uploadLambdaCode(SEED_USER_ID, lambda.lambdaId, buildConfig());

        assertTrue(upload.uploadId > 0);
        assertEquals(upload.uploadId, lambdaDao.getLambda(lambda.lambdaId).codeUploadId);

        LambdaCodeUpload processing = workerService.getUploadForProcessing(lambda.lambdaId, upload.uploadId);
        assertNotNull(processing);
        assertEquals(UploadStatus.IN_PROGRESS, processing.status);

        processing.functionName = "test-function:2";
        processing.asyncFunctionName = "test-function:1";
        processing.memory = 1024;
        processing.timeout = 300;
        processing.status = UploadStatus.COMPLETED;

        assertTrue(workerService.commitUpload(processing));

        LambdaCodeUpload completed = lambdaCodeUploadDao.getLambdaCodeUpload(upload.uploadId);
        LambdaVersion deployedVersion = CollectionOps.findAny(
            lambdaDao.getLambdaVersions(lambda.lambdaId, null, null),
            version -> version.lambdaVersionId == upload.lambdaVersionId);

        assertEquals(UploadStatus.COMPLETED, completed.status);
        assertEquals("test-function:2", completed.functionName);
        assertEquals("test-function:1", completed.asyncFunctionName);
        assertNull(lambdaDao.getLambda(lambda.lambdaId).codeUploadId);
        assertNotNull(deployedVersion);
        assertEquals(LambdaVersionStatus.TESTING, deployedVersion.status);
        assertEquals(upload.uploadId, deployedVersion.codeUploadId);
        assertEquals("test-function:2", deployedVersion.functionName);
        assertEquals("test-function:1", deployedVersion.asyncFunctionName);
        assertEquals(1024, deployedVersion.memory);
        assertEquals(300, deployedVersion.timeout);
    }

    @Test
    void expiredUpload_isFailedAndLateWorkerCannotCommit() {
        Lambda lambda = insertConfiguredLambda();
        LambdaCodeUpload upload = service.uploadLambdaCode(SEED_USER_ID, lambda.lambdaId, buildConfig());

        LambdaCodeUpload processing = workerService.getUploadForProcessing(lambda.lambdaId, upload.uploadId);
        assertNotNull(processing);
        expireProcessing(processing);
        assertTrue(containsUpload(
            lambdaCodeUploadDao.getExpiredLambdaCodeUploads(Datetime.now(), 10), upload.uploadId));

        LambdaCodeUpload failed = workerService.failExpiredUpload(lambda.lambdaId, upload.uploadId, Datetime.now());
        assertNotNull(failed);
        assertEquals(UploadStatus.FAILED, failed.status);
        assertEquals("Deployment did not complete before the timeout", failed.message);
        assertNull(lambdaDao.getLambda(lambda.lambdaId).codeUploadId);

        processing.status = UploadStatus.COMPLETED;
        assertFalse(workerService.commitUpload(processing));

        LambdaCodeUpload nextUpload = service.uploadLambdaCode(SEED_USER_ID, lambda.lambdaId, buildConfig());
        LambdaCodeUpload nextProcessing = workerService.getUploadForProcessing(lambda.lambdaId, nextUpload.uploadId);
        assertNotNull(nextProcessing);
        expireProcessing(nextProcessing);
        LambdaCodeUpload replacement = service.uploadLambdaCode(SEED_USER_ID, lambda.lambdaId, buildConfig());

        LambdaCodeUpload superseded = lambdaCodeUploadDao.getLambdaCodeUpload(nextUpload.uploadId);
        assertEquals(UploadStatus.FAILED, superseded.status);
        assertEquals("Superseded by uploadId=" + replacement.uploadId, superseded.message);
        assertEquals(replacement.uploadId, lambdaDao.getLambda(lambda.lambdaId).codeUploadId);

        nextProcessing.status = UploadStatus.COMPLETED;
        assertFalse(workerService.commitUpload(nextProcessing));
        assertEquals(replacement.uploadId, lambdaDao.getLambda(lambda.lambdaId).codeUploadId);
    }

    private Lambda insertConfiguredLambda() {
        Lambda lambda = new Lambda();
        lambda.lambdaName = LAMBDA_NAME;
        lambda.devTeamId = SEED_TEAM_ID;
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        service.setLambdaConfiguration(SEED_USER_ID, lambda.lambdaId, minimalConfiguration());
        return lambda;
    }

    private void expireProcessing(LambdaCodeUpload upload) {
        upload.status = UploadStatus.FAILED;
        upload.statusDate = Datetime.now();
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);

        upload.status = UploadStatus.IN_PROGRESS;
        upload.statusDate = new Datetime(0);
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
    }

    private static boolean containsUpload(List<LambdaCodeUpload> uploads, int uploadId) {
        for (LambdaCodeUpload upload : uploads) {
            if (upload.uploadId == uploadId) return true;
        }
        return false;
    }

    private static LambdaConfig minimalConfiguration() {
        LambdaConfig config = new LambdaConfig();
        config.bump = VersionBump.MINOR;
        config.trigger = TriggerEvent.TRIGGER_LOCATION_EVENT;
        return config;
    }

    private static BuildConfig buildConfig() {
        try {
            Constructor<BuildConfig> constructor = BuildConfig.class.getDeclaredConstructor(
                byte.class, String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                BuildConfig.ARCH_X86,
                "public.ecr.aws/lambda/python:3.13-x86_64",
                "amd64/python:3.13");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot create BuildConfig test fixture", e);
        }
    }
}
