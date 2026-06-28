package ch.swissqcommerce.backend.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.config.LogisticsConfig;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LogisticsPolicyTest {

    private LogisticsConfig config;
    private ObjectMapper objectMapper;
    private LogisticsPolicy policy;

    @BeforeEach
    public void setUp() {
        config = mock(LogisticsConfig.class);
        objectMapper = new ObjectMapper();

        when(config.getCostSavingsThreshold()).thenReturn(2.00);
        when(config.getLongHaulMiles()).thenReturn(500.0);

        policy = new LogisticsPolicy(objectMapper, config);
    }

    @Test
    public void testEvaluate_AutoApprove_ClearSavings() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":-3.50,\"split_shipment\":false,\"distance_miles\":120.0,\"has_fragile_high_value\":false}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("approved", decision.status());
        assertEquals("auto_approve_clear_savings", decision.reason());
    }

    @Test
    public void testEvaluate_Hitl_SplitShipment() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":-3.50,\"split_shipment\":true,\"distance_miles\":120.0,\"has_fragile_high_value\":false}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertEquals("split_shipment_requires_ops", decision.reason());
        assertEquals("routing", decision.assigneeRole());
    }

    @Test
    public void testEvaluate_Hitl_LongHaul() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":-3.50,\"split_shipment\":false,\"distance_miles\":600.0,\"has_fragile_high_value\":false}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertEquals("long_haul_requires_review", decision.reason());
    }

    @Test
    public void testEvaluate_Hitl_Fragile() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":-3.50,\"split_shipment\":false,\"distance_miles\":120.0,\"has_fragile_high_value\":true}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertEquals("high_value_fragile", decision.reason());
    }

    @Test
    public void testEvaluate_Hitl_CostIncrease() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":1.50,\"split_shipment\":false,\"distance_miles\":120.0,\"has_fragile_high_value\":false}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertEquals("cost_increase_requires_approval", decision.reason());
    }

    @Test
    public void testEvaluate_Hitl_DefaultUnderThreshold() {
        AgentSuggestionEntity s =
                AgentSuggestionEntity.builder()
                        .recommendation(
                                "{\"shipping_cost_delta\":-0.50,\"split_shipment\":false,\"distance_miles\":120.0,\"has_fragile_high_value\":false}")
                        .build();

        PolicyDecision decision = policy.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertEquals("default_logistics_hitl", decision.reason());
    }
}
