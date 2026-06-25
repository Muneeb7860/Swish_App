package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.service.AiOrchestrationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Routing Agent: delivery optimization suggestions, ETA improvement ideas, clustering. */
@Component
public class RoutingAgent {

    private static final Logger log = LoggerFactory.getLogger(RoutingAgent.class);
    private final AiOrchestrationService aiService;
    private final DarkStoreRepository darkStoreRepo;

    public RoutingAgent(AiOrchestrationService aiService, DarkStoreRepository darkStoreRepo) {
        this.aiService = aiService;
        this.darkStoreRepo = darkStoreRepo;
    }

    public AgentSuggestion analyze() {
        try {
            long totalStores = darkStoreRepo.count();
            String prompt =
                    "You are a routing and logistics optimizer for a quick-commerce delivery fleet"
                            + " with "
                            + totalStores
                            + " dark stores. Suggest ONE specific dispatch or routing optimization."
                            + " Respond in exactly this format: ACTION: <routing optimization"
                            + " proposal> | CONFIDENCE: <0.0-1.0> | IMPACT: <low|medium|high> |"
                            + " REASON: <why>";

            String response =
                    aiService
                            .executeLocalTask(prompt)
                            .collectList()
                            .map(list -> String.join("", list))
                            .block(Duration.ofSeconds(10));

            return parseResponse(response);
        } catch (Exception e) {
            log.warn(
                    "RoutingAgent analysis failed, returning deterministic fallback: {}",
                    e.getMessage());
            return AgentSuggestion.of(
                    "routing",
                    "Consolidate adjacent orders to optimize dispatch batches",
                    0.7,
                    "AI model unavailable — routing optimization heuristic applied",
                    "medium");
        }
    }

    private AgentSuggestion parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return fallback("Empty AI response");
        }
        try {
            String action = extractField(response, "ACTION");
            double confidence = parseDouble(extractField(response, "CONFIDENCE"), 0.7);
            String impact = extractField(response, "IMPACT").toLowerCase();
            String reason = extractField(response, "REASON");

            if (!impact.matches("low|medium|high")) impact = "medium";

            return AgentSuggestion.of("routing", action, confidence, reason, impact);
        } catch (Exception e) {
            log.warn("RoutingAgent parse failed: {}", e.getMessage());
            return fallback("AI response format unrecognized");
        }
    }

    private AgentSuggestion fallback(String reason) {
        return AgentSuggestion.of(
                "routing",
                "Review peak hours routing configuration manually",
                0.6,
                reason,
                "medium");
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
