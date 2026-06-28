package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.service.AiOrchestrationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Operations Agent: demand prediction, stock suggestions, bottleneck detection. Reads inventory
 * state, builds a domain prompt, parses AI response → AgentSuggestion.
 */
@Component
public class OpsAgent {

    private static final Logger log = LoggerFactory.getLogger(OpsAgent.class);
    private final AiOrchestrationService aiService;
    private final InventoryRepository inventoryRepo;

    public OpsAgent(AiOrchestrationService aiService, InventoryRepository inventoryRepo) {
        this.aiService = aiService;
        this.inventoryRepo = inventoryRepo;
    }

    public AgentSuggestion analyze() {
        try {
            long totalItems = inventoryRepo.count();
            String prompt =
                    "You are an operations analyst for a quick-commerce dark store with "
                            + totalItems
                            + " SKUs. Analyze current inventory levels and suggest ONE specific"
                            + " restocking action. Respond in exactly this format: ACTION: <what to"
                            + " do> | CONFIDENCE: <0.0-1.0> | IMPACT: <low|medium|high> | REASON:"
                            + " <why>";

            String response =
                    aiService
                            .executeLocalTask(prompt)
                            .collectList()
                            .map(list -> String.join("", list))
                            .block(Duration.ofSeconds(10));

            return parseResponse(response);
        } catch (Exception e) {
            log.warn(
                    "OpsAgent analysis failed, returning deterministic fallback: {}",
                    e.getMessage());
            return AgentSuggestion.of(
                    "inventory",
                    "Review low-stock items and trigger standard reorder",
                    0.5,
                    "AI model unavailable — deterministic fallback applied",
                    "medium");
        }
    }

    private AgentSuggestion parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return fallback("Empty AI response");
        }
        try {
            String action = extractField(response, "ACTION");
            double confidence = parseDouble(extractField(response, "CONFIDENCE"), 0.5);
            String impact = extractField(response, "IMPACT").toLowerCase();
            String reason = extractField(response, "REASON");

            if (!impact.matches("low|medium|high")) impact = "medium";

            return AgentSuggestion.of("inventory", action, confidence, reason, impact);
        } catch (Exception e) {
            log.warn("OpsAgent parse failed: {}", e.getMessage());
            return fallback("AI response format unrecognized");
        }
    }

    private AgentSuggestion fallback(String reason) {
        return AgentSuggestion.of(
                "inventory", "Review inventory levels manually", 0.4, reason, "low");
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
