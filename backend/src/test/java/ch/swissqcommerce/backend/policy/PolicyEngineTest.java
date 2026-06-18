package ch.swissqcommerce.backend.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.agent.AgentSuggestion;
import ch.swissqcommerce.backend.model.SystemConfiguration;
import ch.swissqcommerce.backend.repository.SystemConfigurationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PolicyEngineTest {

    @Mock
    private SystemConfigurationRepository configRepo;

    @InjectMocks
    private PolicyEngine policyEngine;

    @Test
    public void testExtractPercentageChange() {
        assertEquals(12.5, PolicyEngine.extractPercentageChange("increase price by 12.5%"));
        assertEquals(5.0, PolicyEngine.extractPercentageChange("decrease price of organic eggs by 5%"));
        assertEquals(0.0, PolicyEngine.extractPercentageChange("no percent change here"));
    }

    @Test
    public void testEvaluatePricing_RejectExceedingMax() {
        mockConfig("pricing.auto_approve_pct", "5");
        mockConfig("pricing.manager_approval_pct", "10");
        mockConfig("pricing.hitl_pct", "15");

        AgentSuggestion s = AgentSuggestion.of("pricing", "increase price of coffee by 18%", 0.9, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("rejected", decision.status());
        assertTrue(decision.reason().contains("exceeds maximum"));
    }

    @Test
    public void testEvaluatePricing_NeedsHumanManagerApproval() {
        mockConfig("pricing.auto_approve_pct", "5");
        mockConfig("pricing.manager_approval_pct", "10");
        mockConfig("pricing.hitl_pct", "15");

        AgentSuggestion s = AgentSuggestion.of("pricing", "increase price of coffee by 12%", 0.9, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertTrue(decision.reason().contains("requires HITL approval"));
    }

    @Test
    public void testEvaluatePricing_NeedsHumanLowConfidence() {
        mockConfig("pricing.auto_approve_pct", "5");
        mockConfig("pricing.manager_approval_pct", "10");
        mockConfig("pricing.hitl_pct", "15");

        AgentSuggestion s = AgentSuggestion.of("pricing", "increase price of coffee by 8%", 0.6, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertTrue(decision.reason().contains("low confidence"));
    }

    @Test
    public void testEvaluatePricing_AutoApproved() {
        mockConfig("pricing.auto_approve_pct", "5");
        mockConfig("pricing.manager_approval_pct", "10");
        mockConfig("pricing.hitl_pct", "15");

        AgentSuggestion s = AgentSuggestion.of("pricing", "increase price of coffee by 3%", 0.9, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("approved", decision.status());
        assertTrue(decision.reason().contains("within auto-approve"));
    }

    @Test
    public void testEvaluateRisk_RejectBelowIgnore() {
        mockConfig("risk.ignore_below_confidence", "0.3");
        mockConfig("risk.auto_approve_confidence", "0.8");

        AgentSuggestion s = AgentSuggestion.of("risk", "block fraudulent IP", 0.25, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("rejected", decision.status());
        assertTrue(decision.reason().contains("below ignore"));
    }

    @Test
    public void testEvaluateRisk_AutoApproveHighConfidence() {
        mockConfig("risk.ignore_below_confidence", "0.3");
        mockConfig("risk.auto_approve_confidence", "0.8");

        AgentSuggestion s = AgentSuggestion.of("risk", "block fraudulent IP", 0.85, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("approved", decision.status());
        assertTrue(decision.reason().contains("auto-approved"));
    }

    @Test
    public void testEvaluateRisk_NeedsHumanInBetween() {
        mockConfig("risk.ignore_below_confidence", "0.3");
        mockConfig("risk.auto_approve_confidence", "0.8");

        AgentSuggestion s = AgentSuggestion.of("risk", "block fraudulent IP", 0.5, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertTrue(decision.reason().contains("requires human decision"));
    }

    @Test
    public void testEvaluateInventory_Approved() {
        mockConfig("inventory.auto_approve_confidence", "0.6");

        AgentSuggestion s = AgentSuggestion.of("inventory", "restock milk", 0.7, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("approved", decision.status());
    }

    @Test
    public void testEvaluateInventory_NeedsHuman() {
        mockConfig("inventory.auto_approve_confidence", "0.6");

        AgentSuggestion s = AgentSuggestion.of("inventory", "restock milk", 0.5, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
    }

    @Test
    public void testEvaluateRouting_NeedsHumanHighImpact() {
        mockConfig("routing.hitl_impact", "high");
        mockConfig("routing.auto_approve_confidence", "0.65");

        AgentSuggestion s = AgentSuggestion.of("routing", "reroute logistics", 0.9, "reason", "high");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
        assertTrue(decision.reason().contains("requires human approval"));
    }

    @Test
    public void testEvaluateRouting_Approved() {
        mockConfig("routing.hitl_impact", "high");
        mockConfig("routing.auto_approve_confidence", "0.65");

        AgentSuggestion s = AgentSuggestion.of("routing", "reroute logistics", 0.7, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("approved", decision.status());
    }

    @Test
    public void testEvaluateSupport_AlwaysApproved() {
        AgentSuggestion s = AgentSuggestion.of("support", "draft support email", 0.5, "reason", "low");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("approved", decision.status());
    }

    @Test
    public void testEvaluateUnknownDomain_NeedsHuman() {
        AgentSuggestion s = AgentSuggestion.of("unknown", "unknown action", 0.9, "reason", "medium");
        PolicyDecision decision = policyEngine.evaluate(s);

        assertEquals("needs_human", decision.status());
    }

    private void mockConfig(String key, String value) {
        SystemConfiguration config = SystemConfiguration.builder()
                .configKey(key)
                .configValue(value)
                .build();
        when(configRepo.findById(key)).thenReturn(Optional.of(config));
    }
}
