package dev.olegz.vf.worker.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.UploadStatus;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaDeploymentServiceTest {

    @Test
    void persistsEachPublishedFunctionWhileUploadRemainsInProgress() {
        List<String> persistedFunctions = new ArrayList<>();
        LambdaCodeUploadDao dao = proxy(LambdaCodeUploadDao.class, (proxy, method, args) -> {
            if (method.getName().equals("updateInProgressLambdaCodeUploadFunctions")) {
                LambdaCodeUpload persisted = (LambdaCodeUpload) args[0];
                assertEquals(UploadStatus.IN_PROGRESS, persisted.status);
                persistedFunctions.add(persisted.functionName + '|' + persisted.asyncFunctionName);
                return true;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        LambdaDeploymentService service = new LambdaDeploymentService(dao, null, null, null, null);
        LambdaCodeUpload upload = newLambdaCodeUpload();

        service.persistPublishedFunction(upload, InvocationLane.DEFAULT, "lambda-function:7");
        service.persistPublishedFunction(upload, InvocationLane.ASYNC, "lambda-function-async:4");

        assertEquals(List.of("lambda-function:7|null", "lambda-function:7|lambda-function-async:4"), persistedFunctions);
        assertEquals(UploadStatus.IN_PROGRESS, upload.status);
    }

    @Test
    void stopsDeploymentWhenPartialStateCanNoLongerBePersisted() {
        LambdaCodeUploadDao dao = proxy(LambdaCodeUploadDao.class, (proxy, method, args) -> false);
        LambdaDeploymentService service = new LambdaDeploymentService(dao, null, null, null, null);
        LambdaCodeUpload upload = newLambdaCodeUpload();

        ApplicationFailureException failure = assertThrows(ApplicationFailureException.class,
            () -> service.persistPublishedFunction(upload, InvocationLane.DEFAULT, "lambda-function:7"));

        assertTrue(failure.getMessage().contains("left IN_PROGRESS"));
    }

    private static LambdaCodeUpload newLambdaCodeUpload() {
        try {
            Constructor<LambdaCodeUpload> constructor = LambdaCodeUpload.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            LambdaCodeUpload upload = constructor.newInstance();
            upload.uploadId = 17;
            upload.status = UploadStatus.IN_PROGRESS;
            return upload;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot create LambdaCodeUpload test fixture", e);
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
