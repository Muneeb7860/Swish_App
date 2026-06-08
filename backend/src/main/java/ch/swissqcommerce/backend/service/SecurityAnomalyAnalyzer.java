package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI-Assisted Security Anomaly Analyzer.
 * Periodically polls the transactional outbox for 'security.anomaly' events,
 * routes them to the AI Orchestration Service (using local Qwen/Ollama or cloud Gemini)
 * to evaluate threat ratings, and outputs containment instructions.
 */
@Service
public class SecurityAnomalyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SecurityAnomalyAnalyzer.class);

    private final OutboxEventRepository outboxEventRepository;
    private final AiOrchestrationService aiOrchestrationService;

    @Autowired
    public SecurityAnomalyAnalyzer(OutboxEventRepository outboxEventRepository,
                                   AiOrchestrationService aiOrchestrationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.aiOrchestrationService = aiOrchestrationService;
    }

    @Scheduled(fixedDelay = 8000)
    @Transactional
    public void analyzeSecurityAnomalies() {
        List<OutboxEvent> anomalies = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING").stream()
                .filter(e -> "security.anomaly".equalsIgnoreCase(e.getEventType()))
                .toList();

        if (anomalies.isEmpty()) {
            return;
        }

        log.info("AI Security Analyst: Found {} pending anomalies to assess.", anomalies.size());

        for (OutboxEvent anomaly : anomalies) {
            String evaluation;
            try {
                String prompt = "Review this security access violation telemetry details: " + anomaly.getPayload() +
                                ". Provide a threat score (LOW/MEDIUM/HIGH) and recommended containment action in 2 sentences.";
                
                // Invoke local model stream, collect and block for scheduler thread
                evaluation = aiOrchestrationService.executeLocalTask(prompt)
                        .collectList()
                        .map(list -> String.join("", list))
                        .block(java.time.Duration.ofSeconds(4));

            } catch (Exception ex) {
                // If local AI or network fails, gracefully execute deterministic rule-based threat classification
                evaluation = executeDeterministicFallback(anomaly.getPayload(), ex.getMessage());
            }

            log.warn("\n[AI SECURITY ANALYST - ASSESSED ANOMALY]\nEVENT ID: {}\nPAYLOAD: {}\nEVALUATION: {}\n",
                    anomaly.getId(), anomaly.getPayload(), evaluation);

            // Mark event analyzed so we do not re-process it
            anomaly.setStatus("ANALYZED");
            outboxEventRepository.save(anomaly);
        }
    }

    private String executeDeterministicFallback(String payload, String errorReason) {
        log.warn("AI Model invocation failed ({}). Executing rule-based fallback threat evaluation.", errorReason);
        if (payload.contains("ROLE_ADMIN") || payload.contains("admin")) {
            return "Threat Rating: HIGH. Critical admin path violation detected. Fallback Action: Flag account for manual ops team security review.";
        }
        return "Threat Rating: LOW. Standard resource access error. Fallback Action: Continue passive telemetry ingestion monitoring.";
    }
}
