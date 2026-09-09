package dev.olegz.vf.common.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventRateTriggerTest {
    @Test
    void observesThresholdAndSilencePeriod() {
        AtomicLong time = new AtomicLong(1_000L);
        AtomicInteger executions = new AtomicInteger();
        EventRateTrigger trigger = new EventRateTrigger(() -> 5, executions::incrementAndGet, 100L, time::get);

        trigger.check(5);
        assertEquals(0, executions.get());

        trigger.check(6);
        assertEquals(1, executions.get());

        time.set(1_099L);
        trigger.check(6);
        assertEquals(1, executions.get());

        time.set(1_100L);
        trigger.check(6);
        assertEquals(2, executions.get());
    }
}
