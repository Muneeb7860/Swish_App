package ch.swissqcommerce.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Conditional configuration for enabling Spring scheduling. Can be disabled in test environments by
 * setting spring.scheduling.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "spring.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {}
