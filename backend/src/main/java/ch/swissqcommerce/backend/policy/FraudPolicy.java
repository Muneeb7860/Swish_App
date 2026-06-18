package ch.swissqcommerce.backend.policy;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FraudPolicy {

    private static final Logger log = LoggerFactory.getLogger(FraudPolicy.class);
    private final ObjectMapper objectMapper;

    public FraudPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PolicyDecision evaluate(AgentSuggestionEntity s) {
        try {
            JsonNode rec = objectMapper.readTree(s.getRecommendation());
            double riskScore = rec.has("risk_score") ? rec.get("risk_score").asDouble() : s.getConfidence().doubleValue();
            double velocity = rec.has("velocity") ? rec.get("velocity").asDouble() : 1.0;

            // Auto-approve: high velocity (3x+) AND high risk score (0.9+)
            if (velocity >= 3.0 && riskScore >= 0.9) {
                return PolicyDecision.approved("auto_approve_high_risk_velocity");
            }
            // HITL: high impact orders need human review
            if ("high".equalsIgnoreCase(s.getImpact())) {
                return PolicyDecision.needsHuman("high_impact_requires_risk_analyst")
                        .withAssigneeRole("risk_analyst");
            }
            // Reject: low confidence fraud signals
            if (riskScore < 0.5) {
                return PolicyDecision.rejected("low_risk_score_reject");
            }
            return PolicyDecision.needsHuman("default_fraud_requires_hitl");
        } catch (Exception e) {
            log.error("FraudPolicy: Failed to parse recommendation JSON for suggestion ID {}: {}", s.getId(), e.getMessage());
            return PolicyDecision.rejected("malformed_recommendation_json");
        }
    }
}
