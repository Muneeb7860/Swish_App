package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.event.core.service.OutboxEventScheduler;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import ch.swissqcommerce.backend.service.AdminService;
import ch.swissqcommerce.backend.service.SecurityAnomalyAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityAnomalyIntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private SecurityAnomalyAnalyzer securityAnomalyAnalyzer;

    @Autowired
    private OutboxEventScheduler outboxEventScheduler;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);

        transactionTemplate.executeWithoutResult(status -> {
            outboxEventRepository.deleteAll();
        });

        // Set security context authentication representing swissadmin
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "swissadmin", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    public void testSuccessfulActionGeneratesAuditTrail() {
        // Run action
        assertNotNull(adminService.injectFault("LATENCY_SPIKE", "Successful telemetry test"));

        // Verify outbox entry
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> "admin.chaos.inject".equals(e.getEventType()) && "PENDING".equals(e.getStatus())));
    }

    @Test
    public void testExceptionGeneratesSecurityAnomalyAndRunsAiAnalysis() {
        // Assert action fails
        assertThrows(IllegalArgumentException.class, () -> {
            adminService.injectFault("INVALID_FAULT_LOG", "Should fail and trigger anomaly logging");
        });

        // Verify outbox contains anomaly event
        List<OutboxEvent> eventsAfterAnomaly = outboxEventRepository.findAll();
        assertTrue(eventsAfterAnomaly.stream().anyMatch(e -> "security.anomaly".equals(e.getEventType()) && "PENDING".equals(e.getStatus())));

        // Execute dynamic AI security anomaly threat analyst scheduler processing
        securityAnomalyAnalyzer.analyzeSecurityAnomalies();

        // Verify anomaly status is updated to ANALYZED
        List<OutboxEvent> eventsAfterAnalysis = outboxEventRepository.findAll();
        assertTrue(eventsAfterAnalysis.stream().anyMatch(e -> "security.anomaly".equals(e.getEventType()) && "ANALYZED".equals(e.getStatus())));
    }

    @Test
    public void testSchemaValidationSuccessAndPublish() {
        // Run action to generate standard audit outbox record
        assertNotNull(adminService.injectFault("LATENCY_SPIKE", "Validate schema success path"));

        // Run scheduler
        outboxEventScheduler.processOutbox();

        // Verify status becomes PUBLISHED because it matches schema constraints
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(events.stream().anyMatch(e -> "admin.chaos.inject".equals(e.getEventType()) && "PUBLISHED".equals(e.getStatus())));
    }

    @Test
    public void testSchemaValidationFailureDLQ() {
        // Insert a malformed outbox event that fails schema requirements (missing required field: 'operator')
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent malformedEvent = OutboxEvent.builder()
                    .aggregateType("SECURITY")
                    .aggregateId("swissadmin")
                    .eventType("security.anomaly")
                    .payload("{\"action\":\"security.anomaly\",\"method\":\"injectFault\",\"timestamp\":\"2026-06-04T12:00:00Z\",\"status\":\"FAILED\",\"error\":\"Anomaly test\"}") // Missing 'operator'
                    .status("PENDING")
                    .build();
            outboxEventRepository.save(malformedEvent);
        });

        // Run scheduler
        outboxEventScheduler.processOutbox();

        // Verify that validation failed and event status is updated to FAILED immediately
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(events.stream().anyMatch(e -> "security.anomaly".equals(e.getEventType()) && "FAILED".equals(e.getStatus())));
    }
}
