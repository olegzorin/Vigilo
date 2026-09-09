package dev.olegz.vf.core.dao.impl;

import java.lang.reflect.Proxy;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaVariable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaVariableDaoImplTest {

    @Test
    void scopesPrivateVariableReadsAndWritesToGeneration() {
        long generation = 123_456L;
        int[] writes = new int[1];
        LambdaRunMapper mapper = proxy((proxy, method, args) -> {
            switch (method.getName()) {
                case "selectLambdaAssignmentVariable":
                    assertEquals(101, args[0]);
                    assertEquals(generation, args[1]);
                    assertEquals("state", args[2]);
                    LambdaVariable variable = new LambdaVariable();
                    variable.value = new byte[]{1, 2, 3};
                    return variable;
                case "updateLambdaAssignmentVariable":
                    assertEquals(101, args[0]);
                    assertEquals(generation, args[1]);
                    assertEquals("state", args[2]);
                    return false;
                case "insertLambdaAssignmentVariable":
                    assertEquals(101, args[0]);
                    assertEquals(generation, args[1]);
                    assertEquals("state", args[2]);
                    writes[0]++;
                    return true;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
        LambdaVariableDaoImpl dao = new LambdaVariableDaoImpl(mapper);

        assertArrayEquals(
            new byte[]{1, 2, 3},
            dao.getLambdaAssignmentVariable(101, generation, "state"));
        dao.putLambdaAssignmentVariable(101, generation, "state", new byte[0]);

        assertEquals(1, writes[0]);
    }

    @Test
    void rejectsPrivateVariableWriteAfterGenerationChanges() {
        LambdaRunMapper mapper = proxy((proxy, method, args) -> switch (method.getName()) {
            case "updateLambdaAssignmentVariable", "insertLambdaAssignmentVariable" -> false;
            default -> defaultValue(method.getReturnType());
        });
        LambdaVariableDaoImpl dao = new LambdaVariableDaoImpl(mapper);

        assertThrows(
            AccessDeniedException.class,
            () -> dao.putLambdaAssignmentVariable(101, 123L, "state", new byte[0]));
    }

    @Test
    void advancesGenerationMonotonicallyThroughMapper() {
        long[] requestedGeneration = new long[1];
        LambdaRunMapper mapper = proxy((proxy, method, args) -> {
            if (method.getName().equals("advanceLambdaAssignmentVariableGeneration")) {
                assertEquals(101, args[0]);
                requestedGeneration[0] = (long) args[1];
                return null;
            }
            if (method.getName().equals("selectLambdaAssignmentVariableGeneration")) {
                return 789L;
            }
            return defaultValue(method.getReturnType());
        });
        LambdaVariableDaoImpl dao = new LambdaVariableDaoImpl(mapper);

        dao.advanceLambdaAssignmentVariableGeneration(101, 456L);
        assertEquals(456L, requestedGeneration[0]);
    }

    private static LambdaRunMapper proxy(java.lang.reflect.InvocationHandler handler) {
        return LambdaRunMapper.class.cast(Proxy.newProxyInstance(
            LambdaRunMapper.class.getClassLoader(),
            new Class<?>[]{LambdaRunMapper.class},
            handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
