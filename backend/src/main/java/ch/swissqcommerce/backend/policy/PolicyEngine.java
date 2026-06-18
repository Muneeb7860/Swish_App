package ch.swissqcommerce.backend.policy;

import ch.swissqcommerce.backend.agent.AgentSuggestion;
import ch.swissqcommerce.backend.repository.SystemConfigurationRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pure rule-based Policy Engine. Zero ML. Zero complexity.
 *
 * <p>Validates agent suggestions against configurable business rules stored in
 * the {@code system_configurations} table. This is the brain that protects
 * the commerce system from unsafe automation.
 *
 * <p>Rule: if it's not approved here, it cannot execute.
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");

    private final SystemConfigurationRepository configRepo;
    private final PricingPolicy pricingPolicy;

    public PolicyEngine(SystemConfigurationRepository configRepo, PricingPolicy pricingPolicy) {
        this.configRepo = configRepo;
        this.pricingPolicy = pricingPolicy;
    }

    /**
     * Evaluate an agent suggestion entity against business rules.
     */
    public PolicyDecision evaluate(ch.swissqcommerce.backend.model.AgentSuggestionEntity s) {
        if ("pricing".equalsIgnoreCase(s.getDomain())) {
            return pricingPolicy.evaluate(s);
        }
        ch.swissqcommerce.backend.agent.AgentSuggestion domainSuggestion = ch.swissqcommerce.backend.agent.AgentSuggestion.of(
                s.getDomain(), "", s.getConfidence().doubleValue(), s.getReason(), s.getImpact());
        return evaluate(domainSuggestion);
    }

    /**
     * Evaluate an agent suggestion against business rules.
     * Returns a PolicyDecision: approved, rejected, needs_human, or expired.
     */
    public PolicyDecision evaluate(AgentSuggestion suggestion) {
        log.info("PolicyEngine evaluating: domain={}, confidence={}, impact={}",
                suggestion.domain(), suggestion.confidence(), suggestion.impact());

        return switch (suggestion.domain()) {
            case "pricing" -> evaluatePricing(suggestion);
            case "risk" -> evaluateRisk(suggestion);
            case "inventory" -> evaluateInventory(suggestion);
            case "routing" -> evaluateRouting(suggestion);
            case "support" -> PolicyDecision.approved("Support suggestions are always safe to return");
            default -> PolicyDecision.needsHuman(
                    "Unknown domain '" + suggestion.domain() + "' — requires human review");
        };
    }

    private PolicyDecision evaluatePricing(AgentSuggestion s) {
        double autoApprovePct = getConfigDouble("pricing.auto_approve_pct", 5.0);
        double managerPct = getConfigDouble("pricing.manager_approval_pct", 10.0);
        double hitlPct = getConfigDouble("pricing.hitl_pct", 15.0);

        double changePct = extractPercentageChange(s.action());

        if (changePct > hitlPct) {
            return PolicyDecision.rejected(
                    String.format("Price change %.1f%% exceeds maximum %.1f%%", changePct, hitlPct));
        }
        if (changePct > managerPct) {
            return PolicyDecision.needsHuman(
                    String.format("Price change %.1f%% requires HITL approval (threshold: %.1f%%)",
                            changePct, managerPct));
        }
        if (changePct > autoApprovePct && s.confidence() < 0.7) {
            return PolicyDecision.needsHuman(
                    String.format("Price change %.1f%% with low confidence %.2f requires review",
                            changePct, s.confidence()));
        }
        return PolicyDecision.approved(
                String.format("Price change %.1f%% within auto-approve threshold", changePct));
    }

    private PolicyDecision evaluateRisk(AgentSuggestion s) {
        double ignoreBelow = getConfigDouble("risk.ignore_below_confidence", 0.3);
        double autoApprove = getConfigDouble("risk.auto_approve_confidence", 0.8);

        if (s.confidence() < ignoreBelow) {
            return PolicyDecision.rejected(
                    String.format("Risk confidence %.2f below ignore threshold %.2f",
                            s.confidence(), ignoreBelow));
        }
        if (s.confidence() >= autoApprove) {
            return PolicyDecision.approved(
                    String.format("High-confidence risk alert %.2f — auto-approved for action",
                            s.confidence()));
        }
        return PolicyDecision.needsHuman(
                String.format("Risk confidence %.2f in review range — requires human decision",
                        s.confidence()));
    }

    private PolicyDecision evaluateInventory(AgentSuggestion s) {
        double threshold = getConfigDouble("inventory.auto_approve_confidence", 0.6);
        if (s.confidence() >= threshold) {
            return PolicyDecision.approved(
                    String.format("Inventory suggestion confidence %.2f above threshold %.2f",
                            s.confidence(), threshold));
        }
        return PolicyDecision.needsHuman(
                String.format("Inventory suggestion confidence %.2f below threshold %.2f",
                        s.confidence(), threshold));
    }

    private PolicyDecision evaluateRouting(AgentSuggestion s) {
        String hitlImpact = getConfigString("routing.hitl_impact", "high");
        double autoApprove = getConfigDouble("routing.auto_approve_confidence", 0.65);

        if (hitlImpact.equalsIgnoreCase(s.impact())) {
            return PolicyDecision.needsHuman(
                    "Routing change with '" + s.impact() + "' impact requires human approval");
        }
        if (s.confidence() >= autoApprove) {
            return PolicyDecision.approved(
                    String.format("Routing suggestion confidence %.2f — auto-approved", s.confidence()));
        }
        return PolicyDecision.needsHuman(
                String.format("Routing confidence %.2f below threshold %.2f",
                        s.confidence(), autoApprove));
    }

    // --- Config helpers ---

    private double getConfigDouble(String key, double defaultValue) {
        return configRepo.findById(key)
                .map(c -> {
                    try {
                        return Double.parseDouble(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid numeric config for key={}: {}", key, c.getConfigValue());
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private String getConfigString(String key, String defaultValue) {
        return configRepo.findById(key)
                .map(c -> c.getConfigValue())
                .orElse(defaultValue);
    }

    /**
     * Extracts percentage from agent action text, e.g. "increase price by 12.5%".
     * Returns 0.0 if no percentage is found.
     */
    public static double extractPercentageChange(String action) {
        if (action == null) return 0.0;
        Matcher m = PERCENT_PATTERN.matcher(action);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
