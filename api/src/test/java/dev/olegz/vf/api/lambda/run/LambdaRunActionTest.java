package dev.olegz.vf.api.lambda.run;

import java.lang.reflect.Proxy;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.core.domain.lambdarun.LambdaKey;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyJwtClaims;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaRunActionTest {

    @Test
    void startsRunUsingAuthenticatedLambdaKeyAndRuntimeMetadata() {
        LambdaKey lambdaKey = lambdaKey();
        Object[][] call = new Object[1][];
        LambdaClientService service = proxy((proxy, method, args) -> {
            if (method.getName().equals("parseLambdaKey")) {
                assertEquals("lambda-key", args[0]);
                return lambdaKey;
            }
            if (method.getName().equals("startRun")) {
                call[0] = args;
                return true;
            }
            return defaultValue(method.getReturnType());
        });

        ActionResponse response = new LambdaRunAction(service).startRun(
            "lambda-key",
            123_456L,
            "aws-request-id",
            "2026/07/28/[$LATEST]stream");

        assertEquals(0, response.resultCode);
        assertEquals(lambdaKey, call[0][0]);
        assertEquals(123_456L, call[0][1]);
        assertEquals("aws-request-id", call[0][2]);
        assertEquals("2026/07/28/[$LATEST]stream", call[0][3]);
    }

    @Test
    void rejectsRunWhenInvocationTokenDoesNotMatchCurrentRunState() {
        LambdaKey lambdaKey = lambdaKey();
        LambdaClientService service = proxy((proxy, method, args) -> {
            if (method.getName().equals("parseLambdaKey")) return lambdaKey;
            if (method.getName().equals("startRun")) return false;
            return defaultValue(method.getReturnType());
        });

        assertThrows(
            AccessDeniedException.class,
            () -> new LambdaRunAction(service).startRun("lambda-key", 123_456L, null, null));
    }

    private static LambdaKey lambdaKey() {
        LambdaKeyJwtClaims claims = new LambdaKeyJwtClaims();
        claims.aid = 11;
        claims.bid = 21;
        claims.lid = 31;
        return new LambdaKey(claims);
    }

    private static LambdaClientService proxy(java.lang.reflect.InvocationHandler handler) {
        return LambdaClientService.class.cast(Proxy.newProxyInstance(
            LambdaClientService.class.getClassLoader(),
            new Class<?>[]{LambdaClientService.class},
            handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
