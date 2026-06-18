package ch.swissqcommerce.backend.domain.governance.core.service;

import ch.swissqcommerce.backend.domain.governance.port.in.OutcomeEvaluationUseCase;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;
import ch.swissqcommerce.backend.model.OutcomeRecord;
import ch.swissqcommerce.backend.repository.ExecutionRecordRepository;
import ch.swissqcommerce.backend.repository.OutcomeRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OutcomeEvaluationServiceImpl implements OutcomeEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutcomeEvaluationServiceImpl.class);

    private final ExecutionRecordRepository executionRecordRepo;
    private final OutcomeRecordRepository outcomeRecordRepo;
    private final ObjectMapper objectMapper;
    private final Map<String, OutcomeProcessor> processors;

    @PersistenceContext
    private EntityManager entityManager;

    public OutcomeEvaluationServiceImpl(
            ExecutionRecordRepository executionRecordRepo,
            OutcomeRecordRepository outcomeRecordRepo,
            ObjectMapper objectMapper,
            List<OutcomeProcessor> processorList) {
        this.executionRecordRepo = executionRecordRepo;
        this.outcomeRecordRepo = outcomeRecordRepo;
        this.objectMapper = objectMapper;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(OutcomeProcessor::domain, Function.identity()));
    }

    @Override
    public void runOutcomeEvaluation() {
        OffsetDateTime twentyFourHoursAgo = OffsetDateTime.now().minusHours(24);

        // Find executed execution records created in the last 24 hours without outcomes
        List<ExecutionRecord> records = entityManager.createQuery(
                "SELECT er FROM ExecutionRecord er " +
                "WHERE er.executed = true " +
                "AND er.createdAt > :cutoff " +
                "AND NOT EXISTS (SELECT o FROM OutcomeRecord o WHERE o.suggestionId = er.suggestion.id)",
                ExecutionRecord.class)
                .setParameter("cutoff", twentyFourHoursAgo)
                .getResultList();

        log.info("OutcomeEvaluationService: Found {} execution records to evaluate.", records.size());

        for (ExecutionRecord er : records) {
            try {
                evaluateRecord(er);
            } catch (Exception e) {
                log.error("OutcomeEvaluationService: Failed to evaluate execution record ID: {}", er.getId(), e);
            }
        }
    }

    private void evaluateRecord(ExecutionRecord er) throws Exception {
        AgentSuggestionEntity suggestion = er.getSuggestion();
        String domain = suggestion.getDomain();

        OutcomeProcessor processor = processors.get(domain);
        if (processor == null) {
            log.error("No OutcomeProcessor found for domain={} trace_id={}", 
                    domain, suggestion.getTraceId());
            return;
        }

        log.info("OutcomeEvaluationService: Evaluating suggestion ID {} with domain processor {}", suggestion.getId(), domain);
        OutcomeResult result = processor.evaluate(er, suggestion);

        String metricsJson = objectMapper.writeValueAsString(result.getMetrics());

        OutcomeRecord outcome = OutcomeRecord.builder()
                .suggestionId(suggestion.getId())
                .suggestion(suggestion)
                .measurementWindow(result.getMeasurementWindow())
                .metrics(metricsJson)
                .success(result.getSuccess())
                .notes(result.getNotes())
                .build();

        entityManager.persist(outcome);
        log.info("OutcomeEvaluationService: Saved OutcomeRecord for suggestion ID: {}. Success: {}, Metrics: {}", 
                suggestion.getId(), result.getSuccess(), metricsJson);

        // Alert check: last 3 suggestions from same agent
        checkDegradedPerformance(suggestion.getAgent() != null ? suggestion.getAgent().getName() : "PricingAgent");
    }

    private void checkDegradedPerformance(String agentName) {
        List<OutcomeRecord> recentOutcomes = entityManager.createQuery(
                "SELECT o FROM OutcomeRecord o " +
                "WHERE o.suggestion.agent.name = :agentName " +
                "ORDER BY o.evaluatedAt DESC",
                OutcomeRecord.class)
                .setParameter("agentName", agentName)
                .setMaxResults(3)
                .getResultList();

        if (recentOutcomes.size() == 3) {
            boolean allFailed = recentOutcomes.stream().allMatch(o -> !o.getSuccess());
            if (allFailed) {
                log.warn("SLACK ALERT: {} accuracy degraded", agentName);
            }
        }
    }
}
