package ch.swissqcommerce.backend.domain.governance.core.service;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RiskOutcomeProcessor implements OutcomeProcessor {

    private static final Logger log = LoggerFactory.getLogger(RiskOutcomeProcessor.class);

    private final JdbcTemplate jdbcTemplate;

    public RiskOutcomeProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String domain() {
        return "risk";
    }

    @Override
    public OutcomeResult evaluate(ExecutionRecord exec, AgentSuggestionEntity suggestion) throws Exception {
        String entityId = suggestion.getEntityId();
        String orderIdStr = entityId;
        if (entityId != null && entityId.contains("=")) {
            orderIdStr = entityId.split("=")[1];
        }

        Integer orderId = null;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            log.warn("RiskOutcomeProcessor: Invalid order ID format: {}", entityId);
        }

        BigDecimal preventedLoss = BigDecimal.ZERO;
        if (orderId != null) {
            preventedLoss = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(o.total_amount), 0)
                FROM oltp.orders o
                WHERE o.order_id = ?
                  AND o.status = 'held'
                """, BigDecimal.class, orderId);
        }

        if (preventedLoss == null) {
            preventedLoss = BigDecimal.ZERO;
        }

        boolean success = preventedLoss.compareTo(BigDecimal.ZERO) > 0;

        Map<String, Object> metrics = Map.of(
                "prevented_chargeback_usd", preventedLoss.doubleValue()
        );

        return OutcomeResult.builder()
                .metrics(metrics)
                .success(success)
                .measurementWindow(String.format("[%s, %s)", exec.getCreatedAt(), exec.getCreatedAt().plusDays(30)))
                .notes(String.format("Risk assessment for order ID %s: prevented loss = %.2f", orderIdStr, preventedLoss))
                .build();
    }
}
