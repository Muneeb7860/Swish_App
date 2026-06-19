package ch.swissqcommerce.backend.config;

import ch.swissqcommerce.backend.domain.governance.core.model.AssigneeRole;
import ch.swissqcommerce.backend.repository.AgentBaselineRepository;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.OutcomeRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AgentMetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsConfiguration.class);

    private final OutcomeRecordRepository outcomeRecordRepo;
    private final AgentBaselineRepository baselineRepo;
    private final HitlQueueRepository hitlQueueRepo;
    private final AgentSuggestionEntityRepository agentSuggestionRepo;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private final AtomicReference<Double> preventedLossUsdTotal = new AtomicReference<>(0.0);
    private final AtomicReference<Double> revenueDeltaUsdTotal = new AtomicReference<>(0.0);

    public AgentMetricsConfiguration(
            OutcomeRecordRepository outcomeRecordRepo,
            AgentBaselineRepository baselineRepo,
            HitlQueueRepository hitlQueueRepo,
            AgentSuggestionEntityRepository agentSuggestionRepo,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.outcomeRecordRepo = outcomeRecordRepo;
        this.baselineRepo = baselineRepo;
        this.hitlQueueRepo = hitlQueueRepo;
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (meterRegistry == null) return;

        // Gotcha workaround: re-sum on startup
        double preventedLoss = outcomeRecordRepo.sumPreventedLossUsd(objectMapper);
        preventedLossUsdTotal.set(preventedLoss);

        double revenueDelta = outcomeRecordRepo.sumRevenueDeltaUsd(objectMapper);
        revenueDeltaUsdTotal.set(revenueDelta);

        // 1. Expose total gauges for accumulated outcome USD values
        Gauge.builder("prevented_loss_usd_total", preventedLossUsdTotal, AtomicReference::get)
                .description("Total prevented loss in USD")
                .tag("domain", "risk")
                .register(meterRegistry);

        Gauge.builder("revenue_delta_usd_total", revenueDeltaUsdTotal, AtomicReference::get)
                .description("Total revenue delta in USD")
                .tag("domain", "pricing")
                .register(meterRegistry);

        // 2. Expose success rate gauges
        Gauge.builder("outcome_success_rate", this, s -> s.calculateOutcomeSuccessRate("pricing"))
                .description("Outcome success rate")
                .tag("domain", "pricing")
                .register(meterRegistry);

        Gauge.builder("outcome_success_rate", this, s -> s.calculateOutcomeSuccessRate("risk"))
                .description("Outcome success rate")
                .tag("domain", "risk")
                .register(meterRegistry);

        // 3. Expose baseline lag
        Gauge.builder("baseline_lag_hours", this, s -> s.getBaselineLagHours())
                .description("Hours since last baseline update")
                .register(meterRegistry);

        // 4. Expose HITL Queue Depth for each AssigneeRole
        for (AssigneeRole role : AssigneeRole.values()) {
            Gauge.builder("hitl_queue_depth", this, s -> s.getHitlQueueDepth(role))
                    .description("Pending HITL tasks queue depth")
                    .tag("assignee_role", role.name().toLowerCase())
                    .register(meterRegistry);
            
            Gauge.builder("hitl_sla_breach_total", this, s -> s.getHitlSlaBreachCount(role))
                    .description("Total expired HITL tasks")
                    .tag("assignee_role", role.name().toLowerCase())
                    .register(meterRegistry);
        }
    }

    @Async("engineTaskExecutor")
    public void updateMetricsAsync(Map<String, Object> metrics) {
        try {
            if (metrics.containsKey("prevented_chargeback_usd")) {
                double val = ((Number) metrics.get("prevented_chargeback_usd")).doubleValue();
                preventedLossUsdTotal.updateAndGet(current -> current + val);
            }
            if (metrics.containsKey("revenue_delta")) {
                double val = ((Number) metrics.get("revenue_delta")).doubleValue();
                revenueDeltaUsdTotal.updateAndGet(current -> current + val);
            }
        } catch (Exception e) {
            log.error("Failed to update metric buffers asynchronously: {}", e.getMessage());
        }
    }

    public double calculateOutcomeSuccessRate(String domain) {
        try {
            long total = outcomeRecordRepo.countByDomain(domain);
            if (total == 0) return 0.0;
            long success = outcomeRecordRepo.countSuccessfulByDomain(domain);
            return (double) success / total;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getBaselineLagHours() {
        try {
            LocalDate lastBaseline = baselineRepo.findMaxDate();
            if (lastBaseline == null) {
                return 48.0; // default stale lag hours if baseline is empty
            }
            return Duration.between(lastBaseline.atStartOfDay(), LocalDateTime.now()).toHours();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getHitlQueueDepth(AssigneeRole role) {
        try {
            return hitlQueueRepo.countByStatusAndType("pending", "agent_" + role.getDomain());
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getHitlSlaBreachCount(AssigneeRole role) {
        try {
            return agentSuggestionRepo.countSlaBreachByDomainAndNow(role.getDomain(), OffsetDateTime.now());
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Getters for testing
    public double getPreventedLossUsdTotal() {
        return preventedLossUsdTotal.get();
    }

    public double getRevenueDeltaUsdTotal() {
        return revenueDeltaUsdTotal.get();
    }
}
