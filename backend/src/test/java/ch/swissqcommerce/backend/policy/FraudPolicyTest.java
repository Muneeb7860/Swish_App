package ch.swissqcommerce.backend.policy;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FraudPolicyTest {

    private FraudPolicy fraudPolicy;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        fraudPolicy = new FraudPolicy(objectMapper);
    }

    @Test
    public void testAutoApprove_HighVelocityHighRisk() {
        String recJson = "{\"risk_score\":0.95,\"velocity\":3.2}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("risk")
                        .recommendation(recJson)
                        .confidence(BigDecimal.valueOf(0.95))
                        .impact("medium")
                        .reason("suspicious billing pattern")
                        .build();

        PolicyDecision decision = fraudPolicy.evaluate(suggestion);
        assertEquals("approved", decision.status());
        assertEquals("auto_approve_high_risk_velocity", decision.reason());
        assertNull(decision.assigneeRole());
    }

    @Test
    public void testHitl_HighImpact() {
        String recJson = "{\"risk_score\":0.85,\"velocity\":1.5}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("risk")
                        .recommendation(recJson)
                        .confidence(BigDecimal.valueOf(0.85))
                        .impact("high")
                        .reason("very high transaction value")
                        .build();

        PolicyDecision decision = fraudPolicy.evaluate(suggestion);
        assertEquals("needs_human", decision.status());
        assertEquals("high_impact_requires_risk_analyst", decision.reason());
        assertEquals("risk_analyst", decision.assigneeRole());
    }

    @Test
    public void testReject_LowRiskScore() {
        String recJson = "{\"risk_score\":0.40,\"velocity\":1.1}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("risk")
                        .recommendation(recJson)
                        .confidence(BigDecimal.valueOf(0.40))
                        .impact("low")
                        .reason("standard purchase pattern")
                        .build();

        PolicyDecision decision = fraudPolicy.evaluate(suggestion);
        assertEquals("rejected", decision.status());
        assertEquals("low_risk_score_reject", decision.reason());
        assertNull(decision.assigneeRole());
    }

    @Test
    public void testHitl_DefaultMediumRisk() {
        String recJson = "{\"risk_score\":0.75,\"velocity\":1.2}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("risk")
                        .recommendation(recJson)
                        .confidence(BigDecimal.valueOf(0.75))
                        .impact("medium")
                        .reason("unusual hours")
                        .build();

        PolicyDecision decision = fraudPolicy.evaluate(suggestion);
        assertEquals("needs_human", decision.status());
        assertEquals("default_fraud_requires_hitl", decision.reason());
        assertNull(decision.assigneeRole());
    }
}
