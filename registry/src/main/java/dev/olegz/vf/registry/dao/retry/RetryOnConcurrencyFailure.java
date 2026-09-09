package dev.olegz.vf.registry.dao.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for triggering around interceptor with retries on ConcurrencyFailureException.
 * If the execution cannot be completed withing specified number of retries, ApplicationFailureException will be thrown with the provided error message or null will be returned.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryOnConcurrencyFailure {
    /** Error message. If set to an empty string, the exception will not be thrown. */
    String value();
    /** number of retries */
    int retryNo() default 10;
    /** sleep time in milliseconds before the next attempt */
    long sleep() default 500;
}
