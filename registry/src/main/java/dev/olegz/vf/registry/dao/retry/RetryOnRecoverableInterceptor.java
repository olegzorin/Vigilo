package dev.olegz.vf.registry.dao.retry;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
public class RetryOnRecoverableInterceptor implements Ordered {
    private int order;

    @Value("30")
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Around("execution(public * dev.olegz.vf..dao.*.*(..))")
    public Object proceed(ProceedingJoinPoint pjp) throws Throwable {
        // Return whether there currently is an actual transaction active.
        // This indicates whether the current thread is associated with an actual transaction rather than just with active transaction synchronization.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return pjp.proceed();
        }

        for (int i = 10; i >= 0; i--) {
            try {
                return pjp.proceed();
            } catch (RecoverableDataAccessException e) {
                if (i == 0) throw e;
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ee) {
                    throw e;
                }
            }
        }

        return null;
    }
}
