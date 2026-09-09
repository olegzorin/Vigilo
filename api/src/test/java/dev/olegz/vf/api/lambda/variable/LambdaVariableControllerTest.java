package dev.olegz.vf.api.lambda.variable;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

class LambdaVariableControllerTest {

    @Test
    void servesBinaryVariablesAndNoContentForMissingValues() {
        LambdaClientService service = proxy((proxy, method, args) -> {
            if (method.getName().equals("getVariable")) {
                return "present".equals(args[1]) ? new byte[]{1, 2, 3} : null;
            }
            return defaultValue(method.getReturnType());
        });
        LambdaVariableController controller =
            new LambdaVariableController(new LambdaVariableAction(service));

        ResponseEntity<byte[]> found =
            controller.getVariable("lambda-key", "present", false);
        ResponseEntity<byte[]> missing =
            controller.getVariable("lambda-key", "missing", true);

        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, found.getHeaders().getContentType());
        assertEquals(3, found.getHeaders().getContentLength());
        assertArrayEquals(new byte[]{1, 2, 3}, found.getBody());
        assertEquals(HttpStatus.NO_CONTENT, missing.getStatusCode());
        assertNull(missing.getBody());
    }

    @Test
    void delegatesVariableWritesAndDeletesWithScope() {
        List<String> calls = new ArrayList<>();
        LambdaClientService service = proxy((proxy, method, args) -> {
            if (method.getName().equals("putVariable") || method.getName().equals("deleteVariable")) {
                calls.add(method.getName() + ':' + args[0] + ':' + args[1] + ':' + args[2]);
            }
            return defaultValue(method.getReturnType());
        });
        LambdaVariableController controller =
            new LambdaVariableController(new LambdaVariableAction(service));

        ActionResponse putResponse =
            controller.putVariable("lambda-key", "private", false, new byte[]{1});
        ActionResponse deleteResponse =
            controller.deleteVariable("lambda-key", "shared", true);

        assertEquals(0, putResponse.resultCode);
        assertEquals(0, deleteResponse.resultCode);
        assertEquals(List.of(
            "putVariable:lambda-key:private:false",
            "deleteVariable:lambda-key:shared:true"), calls);
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
