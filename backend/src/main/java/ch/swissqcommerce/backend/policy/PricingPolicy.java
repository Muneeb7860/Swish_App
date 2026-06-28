package ch.swissqcommerce.backend.policy;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PricingPolicy {

    private static final Logger log = LoggerFactory.getLogger(PricingPolicy.class);
    private final ObjectMapper objectMapper;

    public PricingPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PolicyDecision evaluate(AgentSuggestionEntity s) {
        try {
            JsonNode rec = objectMapper.readTree(s.getRecommendation());
            double oldPrice = rec.get("old_value").asDouble();
            double newPrice = rec.get("new_value").asDouble();

            if (s.getConfidence().doubleValue() < 0.6) {
                return PolicyDecision.rejected("low_confidence_reject");
            }

            if ("low".equalsIgnoreCase(s.getImpact())
                    && s.getConfidence().doubleValue() >= 0.8
                    && newPrice < oldPrice * 1.05) {
                return PolicyDecision.approved("auto_approve_low_impact");
            }
            return PolicyDecision.needsHuman("default_requires_hitl");
        } catch (Exception e) {
            log.error(
                    "PricingPolicy: Failed to parse recommendation JSON for suggestion ID {}: {}",
                    s.getId(),
                    e.getMessage());
            return PolicyDecision.rejected("malformed_recommendation_json");
        }
    }
}
