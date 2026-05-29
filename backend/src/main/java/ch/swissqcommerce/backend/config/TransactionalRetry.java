package ch.swissqcommerce.backend.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for enabling automatic retries on Optimistic Locking failures.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TransactionalRetry {
    int maxRetries() default 3;
    long backoffMs() default 100;
}
