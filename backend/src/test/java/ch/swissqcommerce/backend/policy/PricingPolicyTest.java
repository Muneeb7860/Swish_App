package ch.swissqcommerce.backend.policy;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PricingPolicyTest {

    private PricingPolicy pricingPolicy;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        pricingPolicy = new PricingPolicy(objectMapper);
    }

    @Test
    public void testEvaluate_AutoApproved() {
        String recommendationJson =
                "{\"action\":\"update_price\",\"old_value\":100.0,\"new_value\":104.5}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.85))
                        .impact("low")
                        .reason("within auto-approve range")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("approved", decision.status());
        assertEquals("auto_approve_low_impact", decision.reason());
    }

    @Test
    public void testEvaluate_NeedsHuman_HighChange() {
        String recommendationJson =
                "{\"action\":\"update_price\",\"old_value\":100.0,\"new_value\":106.0}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.85))
                        .impact("low")
                        .reason("exceeds 5% change threshold")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("needs_human", decision.status());
        assertEquals("default_requires_hitl", decision.reason());
    }

    @Test
    public void testEvaluate_NeedsHuman_LowConfidence() {
        String recommendationJson =
                "{\"action\":\"update_price\",\"old_value\":100.0,\"new_value\":104.0}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.75))
                        .impact("low")
                        .reason("low confidence")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("needs_human", decision.status());
        assertEquals("default_requires_hitl", decision.reason());
    }

    @Test
    public void testEvaluate_NeedsHuman_HighImpact() {
        String recommendationJson =
                "{\"action\":\"update_price\",\"old_value\":100.0,\"new_value\":104.0}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.90))
                        .impact("high")
                        .reason("high impact")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("needs_human", decision.status());
        assertEquals("default_requires_hitl", decision.reason());
    }

    @Test
    public void testEvaluate_MalformedJson_Rejected() {
        String recommendationJson = "{malformed json}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.90))
                        .impact("low")
                        .reason("bad json")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("rejected", decision.status());
        assertEquals("malformed_recommendation_json", decision.reason());
    }

    @Test
    public void testEvaluate_Rejected_VeryLowConfidence() {
        String recommendationJson =
                "{\"action\":\"update_price\",\"old_value\":100.0,\"new_value\":104.0}";
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(0.50))
                        .impact("low")
                        .reason("very low confidence")
                        .build();

        PolicyDecision decision = pricingPolicy.evaluate(suggestion);
        assertEquals("rejected", decision.status());
        assertEquals("low_confidence_reject", decision.reason());
    }
}
