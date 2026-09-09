package dev.olegz.vf.core.dao.impl;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaPendingInputRecord;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaRunStateDaoImplTest {
    @Test
    void enqueuePendingInputInsertsOnlyWhenNoRowWasRecycled() {
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger inserts = new AtomicInteger();
        LambdaRunMapper recyclingMapper = mapper(true, updates, inserts);
        LambdaRunMapper insertingMapper = mapper(false, updates, inserts);
        LambdaPendingInputRecord input = new LambdaPendingInputRecord(10L, new byte[]{1, 2});

        new LambdaRunStateDaoImpl(recyclingMapper).enqueuePendingInput(
            1, InvocationLane.DEFAULT, input, false, 8192, 10L, 0);
        new LambdaRunStateDaoImpl(insertingMapper).enqueuePendingInput(
            1, InvocationLane.DEFAULT, input, false, 8192, 10L, 0);

        assertEquals(2, updates.get());
        assertEquals(1, inserts.get());
    }

    private static LambdaRunMapper mapper(boolean updated, AtomicInteger updates, AtomicInteger inserts) {
        return (LambdaRunMapper) Proxy.newProxyInstance(
            LambdaRunMapper.class.getClassLoader(),
            new Class<?>[]{LambdaRunMapper.class},
            (_, method, _) -> switch (method.getName()) {
                case "updateLambdaPendingInput" -> {
                    updates.incrementAndGet();
                    yield updated;
                }
                case "insertLambdaPendingInput" -> {
                    inserts.incrementAndGet();
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
