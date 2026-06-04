package ch.swissqcommerce.backend.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to flag methods that require automated security audit trails
 * written to the transactional outbox.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityAudit {
    /**
     * The name/identifier of the security action (e.g. 'ADMIN_FAULT_INJECT', 'GDPR_PROFILE_PURGE').
     */
    String action();
}
