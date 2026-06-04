package com.platform.core.checkout.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableScheduling
public class AiEvaluationTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationTimeoutSweeper.class);
    private final JdbcTemplate jdbcTemplate;

    public AiEvaluationTimeoutSweeper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Saga Timeout Compensating Transaction.
     * Runs every 10 seconds.
     * Sweeps for Edge AI evaluations that have been PENDING for >45 seconds.
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void sweepStalledEvaluations() {
        log.debug("Sweeping for stalled AI evaluations...");

        // Note: Using PostgreSQL specific interval syntax.
        String sql = """
                UPDATE wholesale_orders 
                SET ai_status = 'HUMAN_TRIAGE', 
                    ai_reasoning = 'SYSTEM_TIMEOUT: Edge AI Node failed to respond within 45s SLA.', 
                    evaluated_at = CURRENT_TIMESTAMP
                WHERE ai_status = 'PENDING' 
                  AND placed_at < CURRENT_TIMESTAMP - INTERVAL '45 seconds'
                """;

        int rowsUpdated = jdbcTemplate.update(sql);

        if (rowsUpdated > 0) {
            log.error("CRITICAL SAGA TIMEOUT: Forced {} stalled B2B orders into HUMAN_TRIAGE.", rowsUpdated);
            // In a full implementation, you would also trigger an alert to the Ops team via the Notification Engine here.
        }
    }
}
