package dev.olegz.vf.core.dao.metric;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LambdaWorkloadMetricAspect implements Ordered {

    private int order;

    @Value("50")
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Around("@annotation(metric)")
    public Object process(ProceedingJoinPoint joinPoint, LambdaWorkloadMetric metric) throws Throwable {
        if (LambdaWorkloadMetric.ENABLED) {
            long startTime = nanoTime();
            boolean success = false;
            try {
                Object result = joinPoint.proceed();
                success = true;
                return result;
            } finally {
                addCallTimeNanos(metric.operation(), startTime, nanoTime(), success);
            }
        }
        return joinPoint.proceed();
    }

    long nanoTime() {
        return System.nanoTime();
    }

    void addCallTimeNanos(LambdaWorkloadMetric.Operation operation, long startTimeNanos, long endTimeNanos,
                          boolean success) {
        LambdaWorkloadMetrics.addCallTimeNanos(operation, startTimeNanos, endTimeNanos, success);
    }
}
