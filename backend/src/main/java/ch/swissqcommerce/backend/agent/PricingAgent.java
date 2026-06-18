package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.service.AiOrchestrationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pricing Agent: suggests price changes (does NOT apply them directly).
 * The output is evaluated by the PolicyEngine.
 */
@Component
public class PricingAgent {

    private static final Logger log = LoggerFactory.getLogger(PricingAgent.class);
    private final AiOrchestrationService aiService;
    private final InventoryRepository inventoryRepo;

    public PricingAgent(AiOrchestrationService aiService, InventoryRepository inventoryRepo) {
        this.aiService = aiService;
        this.inventoryRepo = inventoryRepo;
    }

    public AgentSuggestion analyze() {
        try {
            long itemCount = inventoryRepo.count();
            String prompt =
                    "You are a dynamic pricing analyst for a quick-commerce app with "
                            + itemCount
                            + " items. Suggest ONE specific price change in percentage (e.g. increase or decrease price by X%)."
                            + " Respond in exactly this format:"
                            + " ACTION: <price change action with percent like 'increase price of coffee by 8.5%'> | CONFIDENCE: <0.0-1.0> | IMPACT: <low|medium|high>"
                            + " | REASON: <why>";

            String response = aiService.executeLocalTask(prompt)
                    .collectList()
                    .map(list -> String.join("", list))
                    .block(Duration.ofSeconds(10));

            return parseResponse(response);
        } catch (Exception e) {
            log.warn("PricingAgent analysis failed, returning deterministic fallback: {}", e.getMessage());
            return AgentSuggestion.of(
                    "pricing",
                    "Increase price of high-demand organic items by 4.5%",
                    0.8,
                    "AI model unavailable — dynamic pricing safe fallback applied",
                    "low");
        }
    }

    private AgentSuggestion parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return fallback("Empty AI response");
        }
        try {
            String action = extractField(response, "ACTION");
            double confidence = parseDouble(extractField(response, "CONFIDENCE"), 0.8);
            String impact = extractField(response, "IMPACT").toLowerCase();
            String reason = extractField(response, "REASON");

            if (!impact.matches("low|medium|high")) impact = "low";

            return AgentSuggestion.of("pricing", action, confidence, reason, impact);
        } catch (Exception e) {
            log.warn("PricingAgent parse failed: {}", e.getMessage());
            return fallback("AI response format unrecognized");
        }
    }

    private AgentSuggestion fallback(String reason) {
        return AgentSuggestion.of("pricing",
                "Increase price of hot beverages by 3.0%", 0.8, reason, "low");
    }

    private static String extractField(String text, String field) {
        int idx = text.toUpperCase().indexOf(field + ":");
        if (idx < 0) return "unknown";
        String after = text.substring(idx + field.length() + 1).trim();
        int pipe = after.indexOf('|');
        return (pipe > 0 ? after.substring(0, pipe) : after).trim();
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
