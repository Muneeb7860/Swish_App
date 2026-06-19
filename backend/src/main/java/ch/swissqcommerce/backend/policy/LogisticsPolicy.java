package ch.swissqcommerce.backend.policy;

import ch.swissqcommerce.backend.config.LogisticsConfig;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogisticsPolicy {

    private static final Logger log = LoggerFactory.getLogger(LogisticsPolicy.class);
    private final ObjectMapper objectMapper;
    private final LogisticsConfig logisticsConfig;

    public LogisticsPolicy(ObjectMapper objectMapper, LogisticsConfig logisticsConfig) {
        this.objectMapper = objectMapper;
        this.logisticsConfig = logisticsConfig;
    }

    public PolicyDecision evaluate(AgentSuggestionEntity s) {
        try {
            JsonNode rec = objectMapper.readTree(s.getRecommendation());
            double costDelta = rec.has("shipping_cost_delta") ? rec.get("shipping_cost_delta").asDouble() : 0.0;
            boolean splitShipment = rec.has("split_shipment") && rec.get("split_shipment").asBoolean();
            double distance = rec.has("distance_miles") ? rec.get("distance_miles").asDouble() : 0.0;
            boolean hasFragile = rec.has("has_fragile_high_value") && rec.get("has_fragile_high_value").asBoolean();

            double savingsThreshold = logisticsConfig.getCostSavingsThreshold();
            double maxDistance = logisticsConfig.getLongHaulMiles();

            // Auto-approve: clear savings (costDelta <= -threshold), single warehouse, short haul, not fragile
            if (costDelta <= -savingsThreshold && !splitShipment && distance < maxDistance && !hasFragile) {
                return PolicyDecision.approved("auto_approve_clear_savings");
            }

            // HITL triggers
            if (splitShipment) {
                return PolicyDecision.needsHuman("split_shipment_requires_ops").withAssigneeRole("routing");
            }
            if (distance >= maxDistance) {
                return PolicyDecision.needsHuman("long_haul_requires_review").withAssigneeRole("routing");
            }
            if (hasFragile) {
                return PolicyDecision.needsHuman("high_value_fragile").withAssigneeRole("routing");
            }
            if (costDelta > 0) {
                return PolicyDecision.needsHuman("cost_increase_requires_approval").withAssigneeRole("routing");
            }

            return PolicyDecision.needsHuman("default_logistics_hitl").withAssigneeRole("routing");
        } catch (Exception e) {
            log.error("LogisticsPolicy: Failed to parse recommendation JSON for suggestion ID {}: {}", s.getId(), e.getMessage());
            return PolicyDecision.rejected("malformed_recommendation_json");
        }
    }
}
