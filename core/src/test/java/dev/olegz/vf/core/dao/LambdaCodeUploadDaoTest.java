package dev.olegz.vf.core.dao;

import java.lang.reflect.Constructor;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
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
class LambdaCodeUploadDaoTest {

    private static final int SEED_USER_ID = 1;
    private static final int SEED_TEAM_ID = 1;

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final LambdaDao lambdaDao;
    private final LambdaCodeUploadDao lambdaCodeUploadDao;

    @Autowired
    LambdaCodeUploadDaoTest(LambdaDao lambdaDao, LambdaCodeUploadDao lambdaCodeUploadDao) {
        this.lambdaDao = lambdaDao;
        this.lambdaCodeUploadDao = lambdaCodeUploadDao;
    }

    @Test
    void lambdaCodeUpload_insertUpdateAndDeletionMetadata_roundTrip() {
        Lambda lambda = insertLambda();
        LambdaVersion lambdaVersion = insertLambdaVersion(lambda.lambdaId);
        LambdaCodeUpload upload = newLambdaCodeUpload(lambda.lambdaId, lambdaVersion.lambdaVersionId);

        lambdaCodeUploadDao.insertLambdaCodeUpload(upload);

        assertTrue(upload.uploadId > 0);
        LambdaCodeUpload inserted = lambdaCodeUploadDao.getLambdaCodeUpload(upload.uploadId);
        assertAll(
            () -> assertEquals(UploadStatus.CREATED, inserted.status),
            () -> assertNotNull(inserted.statusDate),
            () -> assertEquals(upload.codeObjectId, inserted.codeObjectId),
            () -> assertEquals(upload.arch, inserted.arch),
            () -> assertEquals(upload.stageImage, inserted.stageImage),
            () -> assertEquals(upload.baseImage, inserted.baseImage),
            () -> assertEquals(upload.repoName, inserted.repoName),
            () -> assertEquals(lambda.lambdaId, inserted.lambda.lambdaId),
            () -> assertEquals(lambda.lambdaName, inserted.lambda.lambdaName),
            () -> assertEquals(lambdaVersion.lambdaVersionId, inserted.lambdaVersion.lambdaVersionId),
            () -> assertEquals(lambdaVersion.version, inserted.lambdaVersion.version),
            () -> assertEquals(lambdaVersion.status, inserted.lambdaVersion.status)
        );

        upload.status = UploadStatus.IN_PROGRESS;
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);
        upload.functionName = "lambda-function:1";
        assertTrue(lambdaCodeUploadDao.updateInProgressLambdaCodeUploadFunctions(upload));

        LambdaCodeUpload partiallyDeployed = lambdaCodeUploadDao.getLambdaCodeUpload(upload.uploadId);
        assertAll(
            () -> assertEquals(UploadStatus.IN_PROGRESS, partiallyDeployed.status),
            () -> assertEquals("lambda-function:1", partiallyDeployed.functionName),
            () -> assertNull(partiallyDeployed.asyncFunctionName)
        );

        upload.status = UploadStatus.COMPLETED;
        upload.statusDate = Datetime.now();
        upload.message = "  deployment completed  ";
        upload.functionName = "lambda-function";
        upload.asyncFunctionName = "lambda-function-async";
        upload.memory = 2048;
        upload.timeout = 300;
        lambdaCodeUploadDao.updateLambdaCodeUpload(upload);

        Datetime deletionDate = Datetime.now();
        lambdaCodeUploadDao.setLambdaCodeUploadFunctionDeletionDate(upload.uploadId, deletionDate, "deleted by cleanup");

        LambdaCodeUpload updated = lambdaCodeUploadDao.getLambdaCodeUpload(upload.uploadId);
        assertAll(
            () -> assertEquals(UploadStatus.COMPLETED, updated.status),
            () -> assertNotNull(updated.statusDate),
            () -> assertEquals("deployment completed", updated.message),
            () -> assertEquals("lambda-function", updated.functionName),
            () -> assertEquals("lambda-function-async", updated.asyncFunctionName),
            () -> assertEquals(2048, updated.memory),
            () -> assertEquals(300, updated.timeout),
            () -> assertNotNull(updated.functionDeletionDate),
            () -> assertEquals("deleted by cleanup", updated.functionDeletionInfo)
        );
    }

    @Test
    void getLambdaCodeUploadsForCleanup_excludesPendingUpload() {
        Lambda lambda = insertLambda();
        LambdaVersion lambdaVersion = insertLambdaVersion(lambda.lambdaId);
        LambdaCodeUpload upload = newLambdaCodeUpload(lambda.lambdaId, lambdaVersion.lambdaVersionId);
        lambdaCodeUploadDao.insertLambdaCodeUpload(upload);
        lambdaDao.updateLambdaUploadId(lambda.lambdaId, upload.uploadId);

        assertFalse(containsUpload(lambdaCodeUploadDao.getLambdaCodeUploadsForCleanup(), upload.uploadId));

        lambdaDao.updateLambdaUploadId(lambda.lambdaId, null);

        assertTrue(containsUpload(lambdaCodeUploadDao.getLambdaCodeUploadsForCleanup(), upload.uploadId));
    }

    private Lambda insertLambda() {
        Lambda lambda = new Lambda();
        lambda.lambdaName = "test_lambda_code_upload";
        lambda.devTeamId = SEED_TEAM_ID;
        lambda.createdAt = Datetime.now();
        lambdaDao.insertLambda(lambda);
        return lambda;
    }

    private LambdaVersion insertLambdaVersion(int lambdaId) {
        LambdaVersion lambdaVersion = new LambdaVersion();
        lambdaVersion.lambdaId = lambdaId;
        lambdaVersion.createdAt = Datetime.now();
        lambdaVersion.statusDate = lambdaVersion.createdAt;
        lambdaVersion.createdBy = SEED_USER_ID;
        lambdaVersion.trigger = 0;
        lambdaVersion.version = "0.1.0";
        lambdaVersion.sequenceNumber = 1;
        lambdaVersion.status = LambdaVersionStatus.DRAFT;
        lambdaDao.insertLambdaVersionConfig(lambdaVersion);
        return lambdaVersion;
    }

    private static LambdaCodeUpload newLambdaCodeUpload(int lambdaId, int lambdaVersionId) {
        LambdaCodeUpload upload = instantiateLambdaCodeUpload();
        upload.lambdaId = lambdaId;
        upload.lambdaVersionId = lambdaVersionId;
        upload.userId = SEED_USER_ID;
        upload.status = UploadStatus.CREATED;
        upload.statusDate = Datetime.now();
        upload.codeObjectId = "code/" + lambdaId + "/test-upload";
        upload.arch = 1;
        upload.stageImage = "python:3.13";
        upload.baseImage = "public.ecr.aws/lambda/python:3.13";
        upload.repoName = "test-lambda-repository";
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

    private static boolean containsUpload(List<LambdaCodeUpload> uploads, int uploadId) {
        for (LambdaCodeUpload upload : uploads) {
            if (upload.uploadId == uploadId) return true;
        }
        return false;
    }
}
