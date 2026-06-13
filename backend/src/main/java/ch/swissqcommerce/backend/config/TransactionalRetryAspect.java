package ch.swissqcommerce.backend.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

/**
 * Aspect handling automatic transaction retries on Concurrency (Optimistic or Pessimistic/Locking) failures.
 * Sets high precedence Order(99) to run outside the standard Spring Transaction manager,
 * allowing target transaction boundaries to roll back and retry cleanly from a fresh state.
 */
@Aspect
@Component
@Order(99)
public class TransactionalRetryAspect {

    @Around("@annotation(retryAnnotation)")
    public Object retry(ProceedingJoinPoint joinPoint, TransactionalRetry retryAnnotation) throws Throwable {
        int maxRetries = retryAnnotation.maxRetries();
        long backoffMs = retryAnnotation.backoffMs();
        int attempts = 0;
        Throwable lastException = null;

        while (attempts < maxRetries) {
            try {
                return joinPoint.proceed();
            } catch (ConcurrencyFailureException e) {
                attempts++;
                lastException = e;
                if (attempts >= maxRetries) {
                    break;
                }
                // Exponential backoff with jitter
                Thread.sleep((long) (backoffMs * Math.pow(attempts, 2) * (0.8 + Math.random() * 0.4)));
            }
        }
        throw lastException;
    }
}
