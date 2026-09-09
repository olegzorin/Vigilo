package dev.olegz.vf.common.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class EventRateMonitorTest {
    @Test
    void triggersAtThresholdAndDropsExpiredEvents() {
        AtomicLong time = new AtomicLong(1_000L);
        RecordingTrigger trigger = new RecordingTrigger(time);
        EventRateMonitor monitor = new EventRateMonitor(100L, 2, 2, time::get, trigger);

        monitor.addEvent();
        monitor.addEvent();
        monitor.addEvent();

        time.set(1_050L);
        monitor.addEvent();
        time.set(1_150L);
        monitor.addEvent();

        assertIterableEquals(List.of(1, 2, 3, 4, 2), trigger.values);
    }

    private static class RecordingTrigger extends EventRateTrigger {
        private final List<Integer> values = new ArrayList<>();

        private RecordingTrigger(AtomicLong time) {
            super(() -> Integer.MAX_VALUE, () -> {}, 0L, time::get);
        }

        @Override
        void check(int value) {
            values.add(value);
        }
    }
}
