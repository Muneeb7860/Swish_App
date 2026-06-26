package ch.swissqcommerce.backend.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.repository.AgentBaselineRepository;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.OutcomeRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AgentMetricsTest {

    private OutcomeRecordRepository outcomeRecordRepo;
    private AgentBaselineRepository baselineRepo;
    private HitlQueueRepository hitlQueueRepo;
    private AgentSuggestionEntityRepository agentSuggestionRepo;
    private SimpleMeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private AgentMetricsConfiguration metricsConfig;

    @BeforeEach
    public void setUp() {
        outcomeRecordRepo = mock(OutcomeRecordRepository.class);
        baselineRepo = mock(AgentBaselineRepository.class);
        hitlQueueRepo = mock(HitlQueueRepository.class);
        agentSuggestionRepo = mock(AgentSuggestionEntityRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();

        when(outcomeRecordRepo.sumPreventedLossUsd(any())).thenReturn(100.0);
        when(outcomeRecordRepo.sumRevenueDeltaUsd(any())).thenReturn(50.0);
        when(outcomeRecordRepo.sumShippingSavingsUsd(any())).thenReturn(30.0);

        metricsConfig =
                new AgentMetricsConfiguration(
                        outcomeRecordRepo,
                        baselineRepo,
                        hitlQueueRepo,
                        agentSuggestionRepo,
                        meterRegistry,
                        objectMapper);
    }

    @Test
    public void testInit_BufferLoadingFromDb() {
        metricsConfig.init();

        assertEquals(100.0, metricsConfig.getPreventedLossUsdTotal());
        assertEquals(50.0, metricsConfig.getRevenueDeltaUsdTotal());
        assertEquals(30.0, metricsConfig.getShippingSavingsUsdTotal());

        assertEquals(100.0, meterRegistry.find("prevented_loss_usd_total").gauge().value());
        assertEquals(50.0, meterRegistry.find("revenue_delta_usd_total").gauge().value());
        assertEquals(30.0, meterRegistry.find("shipping_savings_usd_total").gauge().value());
    }

    @Test
    public void testUpdateMetricsAsync_AccumulatesCorrectly() {
        metricsConfig.init();

        metricsConfig.updateMetricsAsync(
                Map.of(
                        "prevented_chargeback_usd", 25.5,
                        "revenue_delta", 10.2,
                        "shipping_savings_usd", 15.3));

        assertEquals(125.5, metricsConfig.getPreventedLossUsdTotal());
        assertEquals(60.2, metricsConfig.getRevenueDeltaUsdTotal());
        assertEquals(45.3, metricsConfig.getShippingSavingsUsdTotal());
    }

    @Test
    public void testCalculateOutcomeSuccessRate() {
        when(outcomeRecordRepo.countByDomain("pricing")).thenReturn(10L);
        when(outcomeRecordRepo.countSuccessfulByDomain("pricing")).thenReturn(8L);
        when(outcomeRecordRepo.countByDomain("routing")).thenReturn(5L);
        when(outcomeRecordRepo.countSuccessfulByDomain("routing")).thenReturn(4L);

        metricsConfig.init();

        double successRate =
                meterRegistry
                        .find("outcome_success_rate")
                        .tags("domain", "pricing")
                        .gauge()
                        .value();
        assertEquals(0.8, successRate, 0.001);

        double logisticsSuccessRate =
                meterRegistry
                        .find("outcome_success_rate")
                        .tags("domain", "logistics")
                        .gauge()
                        .value();
        assertEquals(0.8, logisticsSuccessRate, 0.001);
    }

    @Test
    public void testGetBaselineLagHours_Calculation() {
        LocalDate mockMaxDate = LocalDate.now().minusDays(1);
        when(baselineRepo.findMaxDate()).thenReturn(mockMaxDate);

        metricsConfig.init();

        double lagHours = meterRegistry.find("baseline_lag_hours").gauge().value();
        assertTrue(lagHours >= 24.0);
    }

    @Test
    public void testGetBaselineLagHours_EmptyBaseline_ReturnsDefault() {
        when(baselineRepo.findMaxDate()).thenReturn(null);

        metricsConfig.init();

        double lagHours = meterRegistry.find("baseline_lag_hours").gauge().value();
        assertEquals(48.0, lagHours);
    }

    @Test
    public void testGetHitlQueueDepth() {
        when(hitlQueueRepo.countByStatusAndType("pending", "agent_risk")).thenReturn(5L);

        metricsConfig.init();

        double depth =
                meterRegistry
                        .find("hitl_queue_depth")
                        .tags("assignee_role", "risk_analyst")
                        .gauge()
                        .value();
        assertEquals(5.0, depth);
    }

    @Test
    public void testGetHitlSlaBreachCount() {
        when(agentSuggestionRepo.countSlaBreachByDomainAndNow(eq("risk"), any())).thenReturn(3L);

        metricsConfig.init();

        double breaches =
                meterRegistry
                        .find("hitl_sla_breach_total")
                        .tags("assignee_role", "risk_analyst")
                        .gauge()
                        .value();
        assertEquals(3.0, breaches);
    }
}
