package dev.olegz.vf.core.dao.metric;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static dev.olegz.vf.core.dao.metric.LambdaWorkloadMetric.Operation.workflowPutNewRun;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaWorkloadMetricAspectTest {

    private static final LambdaWorkloadMetric METRIC = metric();

    @Test
    void recordsSuccessfulCall() throws Throwable {
        RecordingAspect aspect = new RecordingAspect();
        Object result = new Object();

        assertSame(result, aspect.process(joinPoint(result, null), METRIC));
        assertEquals(1, aspect.recordedCalls);
        assertEquals(10L, aspect.startTimeNanos);
        assertEquals(25L, aspect.endTimeNanos);
        assertSame(workflowPutNewRun, aspect.operation);
        assertTrue(aspect.success);
    }

    @Test
    void recordsFailedCallAndRethrowsOriginalException() {
        RecordingAspect aspect = new RecordingAspect();
        IllegalStateException failure = new IllegalStateException("database failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> aspect.process(joinPoint(null, failure), METRIC));

        assertSame(failure, thrown);
        assertEquals(1, aspect.recordedCalls);
        assertEquals(10L, aspect.startTimeNanos);
        assertEquals(25L, aspect.endTimeNanos);
        assertSame(workflowPutNewRun, aspect.operation);
        assertFalse(aspect.success);
    }

    private static ProceedingJoinPoint joinPoint(Object result, Throwable failure) {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
            LambdaWorkloadMetricAspectTest.class.getClassLoader(),
            new Class<?>[]{ProceedingJoinPoint.class},
            (proxy, method, args) -> {
                if (method.getName().equals("proceed")) {
                    if (failure != null) throw failure;
                    return result;
                }
                return null;
            });
    }

    private static LambdaWorkloadMetric metric() {
        try {
            Method method = MetricTarget.class.getDeclaredMethod("call");
            return method.getAnnotation(LambdaWorkloadMetric.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static class RecordingAspect extends LambdaWorkloadMetricAspect {
        private int timeIndex;
        private int recordedCalls;
        private LambdaWorkloadMetric.Operation operation;
        private long startTimeNanos;
        private long endTimeNanos;
        private boolean success;

        @Override
        long nanoTime() {
            return timeIndex++ == 0 ? 10L : 25L;
        }

        @Override
        void addCallTimeNanos(LambdaWorkloadMetric.Operation operation, long startTimeNanos, long endTimeNanos,
                              boolean success) {
            recordedCalls++;
            this.operation = operation;
            this.startTimeNanos = startTimeNanos;
            this.endTimeNanos = endTimeNanos;
            this.success = success;
        }
    }

    private static class MetricTarget {
        @LambdaWorkloadMetric(operation = workflowPutNewRun)
        void call() {
        }
    }
}
