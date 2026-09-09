package dev.olegz.vf.common.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkloadTriggerTest {
    @Test
    void observesConditionAndSilencePeriod() {
        AtomicLong time = new AtomicLong(1_000L);
        AtomicInteger executions = new AtomicInteger();
        WorkloadTrigger trigger = new WorkloadTrigger(
            (count, duration) -> count >= 3 && duration >= 100L,
            executions::incrementAndGet,
            100L,
            time::get);

        trigger.check(2, 100L);
        trigger.check(3, 99L);
        assertEquals(0, executions.get());

        trigger.check(3, 100L);
        assertEquals(1, executions.get());

        time.set(1_099L);
        trigger.check(3, 100L);
        assertEquals(1, executions.get());

        time.set(1_100L);
        trigger.check(3, 100L);
        assertEquals(2, executions.get());
    }
}
