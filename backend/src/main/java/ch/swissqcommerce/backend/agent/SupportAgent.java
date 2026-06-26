package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.service.AiOrchestrationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Support Agent: generates support ticket draft replies and summarizations. */
@Component
public class SupportAgent {

    private static final Logger log = LoggerFactory.getLogger(SupportAgent.class);
    private final AiOrchestrationService aiService;
    private final HitlQueueRepository hitlRepo;

    public SupportAgent(AiOrchestrationService aiService, HitlQueueRepository hitlRepo) {
        this.aiService = aiService;
        this.hitlRepo = hitlRepo;
    }

    public AgentSuggestion analyze() {
        try {
            long totalTickets = hitlRepo.count();
            String prompt =
                    "You are a customer support automation agent for a quick-commerce platform with"
                            + " "
                            + totalTickets
                            + " open tickets. Suggest ONE specific customer response improvement"
                            + " draft or summarization. Respond in exactly this format: ACTION:"
                            + " <support response draft or summary suggestion> | CONFIDENCE:"
                            + " <0.0-1.0> | IMPACT: <low|medium|high> | REASON: <why>";

            String response =
                    aiService
                            .executeLocalTask(prompt)
                            .collectList()
                            .map(list -> String.join("", list))
                            .block(Duration.ofSeconds(10));

            return parseResponse(response);
        } catch (Exception e) {
            log.warn(
                    "SupportAgent analysis failed, returning deterministic fallback: {}",
                    e.getMessage());
            return AgentSuggestion.of(
                    "support",
                    "Draft response for delayed order apology and voucher issue",
                    0.9,
                    "AI model unavailable — standard customer support auto-draft applied",
                    "low");
        }
    }

    private AgentSuggestion parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return fallback("Empty AI response");
        }
        try {
            String action = extractField(response, "ACTION");
            double confidence = parseDouble(extractField(response, "CONFIDENCE"), 0.9);
            String impact = extractField(response, "IMPACT").toLowerCase();
            String reason = extractField(response, "REASON");

            if (!impact.matches("low|medium|high")) impact = "low";

            return AgentSuggestion.of("support", action, confidence, reason, impact);
        } catch (Exception e) {
            log.warn("SupportAgent parse failed: {}", e.getMessage());
            return fallback("AI response format unrecognized");
        }
    }

    private AgentSuggestion fallback(String reason) {
        return AgentSuggestion.of(
                "support", "Review open support tickets manually", 0.7, reason, "low");
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
