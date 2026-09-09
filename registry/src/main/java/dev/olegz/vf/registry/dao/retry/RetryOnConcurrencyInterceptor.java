package dev.olegz.vf.registry.dao.retry;

import java.util.Arrays;

import dev.olegz.vf.common.ApplicationFailureException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RetryOnConcurrencyInterceptor implements Ordered {
    private int order;

    @Value("40")
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Around("@annotation(retry)")
    public Object proceed(ProceedingJoinPoint pjp, RetryOnConcurrencyFailure retry) throws Throwable {
        ConcurrencyFailureException ex = null;
        for (int i = retry.retryNo(); i >= 0; i--) {
            try {
                return pjp.proceed();
            } catch (ConcurrencyFailureException e) {
                ex = e;
                if ((i > 0) && (retry.sleep() > 0)) {
                    try {
                        Thread.sleep(retry.sleep());
                    } catch (InterruptedException ee) {
                        break;
                    }
                }
            }
        }

        if (!retry.value().isBlank()) {
            Object[] args = pjp.getArgs();
            throw new ApplicationFailureException(retry.value() +
                (args == null || args.length == 0 ? "" : args.length == 1 ? args[0] : Arrays.toString(args)), ex);
        }
        return null;
    }
}
