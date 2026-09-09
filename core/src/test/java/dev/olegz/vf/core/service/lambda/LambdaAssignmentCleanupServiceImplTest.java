package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaAssignmentCleanupDao;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.dao.LambdaVariableDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAssignmentCleanupServiceImplTest {
    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void enqueueLambdaAssignmentCleanupStoresFutureCleanupDate() {
        int lambdaAssignmentId = 101;
        Datetime[] cleanupAfter = new Datetime[1];
        LambdaAssignmentCleanupDao cleanupDao = proxy(LambdaAssignmentCleanupDao.class, (method, args) -> {
            if (method.getName().equals("enqueueLambdaAssignmentCleanup")) {
                assertEquals(lambdaAssignmentId, args[0]);
                cleanupAfter[0] = (Datetime) args[1];
            }
            return defaultValue(method.getReturnType());
        });
        LambdaAssignmentCleanupService service = service(
            cleanupDao,
            proxy(LambdaVariableDao.class, (method, args) -> defaultValue(method.getReturnType())),
            proxy(LambdaRunDao.class, (method, args) -> defaultValue(method.getReturnType())));

        long before = System.currentTimeMillis();
        service.enqueueLambdaAssignmentCleanup(lambdaAssignmentId);

        assertTrue(cleanupAfter[0].getTime() >= before);
    }

    @Test
    void cleanupNextLambdaAssignmentTakesQueueEntryThenDeletesRuntimeData() {
        int lambdaAssignmentId = 101;
        List<String> operations = new ArrayList<>();
        LambdaAssignmentCleanupDao cleanupDao = proxy(LambdaAssignmentCleanupDao.class, (method, args) -> {
            return switch (method.getName()) {
                case "takeNextLambdaAssignmentForCleanup" -> {
                    operations.add("take");
                    yield lambdaAssignmentId;
                }
                default -> defaultValue(method.getReturnType());
            };
        });
        LambdaVariableDao lambdaVariableDao = proxy(LambdaVariableDao.class, (method, args) -> {
            if (method.getName().equals("deleteLambdaAssignmentVariables")) operations.add("variables");
            return defaultValue(method.getReturnType());
        });
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (method, args) -> {
            if (method.getName().equals("deleteLambdaAssignmentRunData")) operations.add("runs");
            return defaultValue(method.getReturnType());
        });
        LambdaAssignmentCleanupService service = service(cleanupDao, lambdaVariableDao, lambdaRunDao);

        Integer result = service.cleanupNextLambdaAssignment();

        assertEquals(lambdaAssignmentId, result);
        assertEquals(List.of("take", "variables", "runs"), operations);
    }

    @Test
    void cleanupNextLambdaAssignmentStopsWhenNoCleanupIsDue() {
        boolean[] runtimeDataDeleted = {false};
        LambdaAssignmentCleanupDao cleanupDao = proxy(LambdaAssignmentCleanupDao.class,
            (method, args) -> defaultValue(method.getReturnType()));
        LambdaVariableDao lambdaVariableDao = proxy(LambdaVariableDao.class, (method, args) -> {
            if (method.getName().equals("deleteLambdaAssignmentVariables")) runtimeDataDeleted[0] = true;
            return defaultValue(method.getReturnType());
        });
        LambdaRunDao lambdaRunDao = proxy(LambdaRunDao.class, (method, args) -> {
            if (method.getName().equals("deleteLambdaAssignmentRunData")) runtimeDataDeleted[0] = true;
            return defaultValue(method.getReturnType());
        });
        LambdaAssignmentCleanupService service = service(cleanupDao, lambdaVariableDao, lambdaRunDao);

        assertNull(service.cleanupNextLambdaAssignment());
        assertFalse(runtimeDataDeleted[0]);
    }

    private static LambdaAssignmentCleanupService service(
        LambdaAssignmentCleanupDao cleanupDao,
        LambdaVariableDao lambdaVariableDao,
        LambdaRunDao lambdaRunDao)
    {
        return new LambdaAssignmentCleanupServiceImpl(cleanupDao, lambdaVariableDao, lambdaRunDao);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (_, method, args) -> invocation.invoke(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        throw new IllegalArgumentException("Unsupported primitive " + type);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }
}
