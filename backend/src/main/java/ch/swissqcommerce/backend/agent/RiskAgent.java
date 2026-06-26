package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import ch.swissqcommerce.backend.service.AiOrchestrationService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Risk Agent: evaluates security anomalies and fraud risk. */
@Component
public class RiskAgent {

    private static final Logger log = LoggerFactory.getLogger(RiskAgent.class);
    private final AiOrchestrationService aiService;
    private final OutboxEventRepository outboxRepo;

    public RiskAgent(AiOrchestrationService aiService, OutboxEventRepository outboxRepo) {
        this.aiService = aiService;
        this.outboxRepo = outboxRepo;
    }

    public AgentSuggestion analyze() {
        try {
            long pendingAnomalies =
                    outboxRepo.findByStatusOrderByCreatedAtAsc("PENDING").stream()
                            .filter(e -> "security.anomaly".equalsIgnoreCase(e.getEventType()))
                            .count();

            String prompt =
                    "You are a quick-commerce risk and fraud intelligence agent. We currently have "
                            + pendingAnomalies
                            + " pending security anomalies. Suggest ONE specific risk mitigation"
                            + " action. Respond in exactly this format: ACTION: <security risk"
                            + " mitigation action> | CONFIDENCE: <0.0-1.0> | IMPACT:"
                            + " <low|medium|high> | REASON: <why>";

            String response =
                    aiService
                            .executeLocalTask(prompt)
                            .collectList()
                            .map(list -> String.join("", list))
                            .block(Duration.ofSeconds(10));

            return parseResponse(response);
        } catch (Exception e) {
            log.warn(
                    "RiskAgent analysis failed, returning deterministic fallback: {}",
                    e.getMessage());
            return AgentSuggestion.of(
                    "risk",
                    "Flag high-frequency guest checkout accounts for validation",
                    0.75,
                    "AI model unavailable — rule-based risk screening applied",
                    "medium");
        }
    }

    private AgentSuggestion parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return fallback("Empty AI response");
        }
        try {
            String action = extractField(response, "ACTION");
            double confidence = parseDouble(extractField(response, "CONFIDENCE"), 0.75);
            String impact = extractField(response, "IMPACT").toLowerCase();
            String reason = extractField(response, "REASON");

            if (!impact.matches("low|medium|high")) impact = "medium";

            return AgentSuggestion.of("risk", action, confidence, reason, impact);
        } catch (Exception e) {
            log.warn("RiskAgent parse failed: {}", e.getMessage());
            return fallback("AI response format unrecognized");
        }
    }

    private AgentSuggestion fallback(String reason) {
        return AgentSuggestion.of(
                "risk", "Review recent security.anomaly events manually", 0.5, reason, "medium");
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
