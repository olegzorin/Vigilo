package dev.olegz.vf.core.dao.handlers;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvocationLaneTypeHandlerTest {
    private final InvocationLaneTypeHandler handler = new InvocationLaneTypeHandler();

    @Test
    void writesLaneName() throws Exception {
        AtomicReference<String> value = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
            if (method.equals("setString")) value.set((String) args[1]);
            return null;
        });

        handler.setNonNullParameter(statement, 1, InvocationLane.ASYNC, null);

        assertEquals("ASYNC", value.get());
    }

    @Test
    void readsLaneName() throws Exception {
        ResultSet resultSet = proxy(ResultSet.class, (method, args) ->
            method.equals("getString") ? "DEFAULT" : null);
        CallableStatement callableStatement = proxy(CallableStatement.class, (method, args) ->
            method.equals("getString") ? "ASYNC" : null);

        assertEquals(InvocationLane.DEFAULT, handler.getNullableResult(resultSet, "lane"));
        assertEquals(InvocationLane.DEFAULT, handler.getNullableResult(resultSet, 1));
        assertEquals(InvocationLane.ASYNC, handler.getNullableResult(callableStatement, 1));
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> invocation.invoke(method.getName(), args)));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}
