package ch.swissqcommerce.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.swissqcommerce.backend.domain.security.adapter.in.web.SecurityController;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
public class SecurityControllerTest {

    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;

    @Mock private HitlQueueRepository hitlQueueRepository;

    @Mock private OutboxEventRepository outboxEventRepository;

    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks private SecurityController securityController;

    @BeforeEach
    public void setUp() {
        securityController.registerAnomalyRatioGauge();
    }

    @Test
    public void testGetAuditTrail() {
        SecurityTrustLedger ledger = new SecurityTrustLedger();
        ledger.setActorId("admin-1");

        when(trustLedgerRepository.findAll()).thenReturn(List.of(ledger));

        ResponseEntity<List<SecurityTrustLedger>> response =
                securityController.getAuditTrail(null, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());

        verify(trustLedgerRepository).findAll();
    }

    @Test
    public void testGetAuditTrailWithParams() {
        SecurityTrustLedger ledger = new SecurityTrustLedger();
        ledger.setActorId("admin-1");

        when(trustLedgerRepository.findByActorTypeAndActorIdOrderByTimestampDesc(
                        "admin", "admin-1"))
                .thenReturn(List.of(ledger));

        ResponseEntity<List<SecurityTrustLedger>> response =
                securityController.getAuditTrail("admin", "admin-1");
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());

        verify(trustLedgerRepository)
                .findByActorTypeAndActorIdOrderByTimestampDesc("admin", "admin-1");
    }

    @Test
    public void testRotateVaultKey() {
        ResponseEntity<Map<String, Object>> response =
                securityController.rotateVaultKey("test reason");
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("test reason", response.getBody().get("reason"));
        assertEquals(true, response.getBody().get("rotated"));
    }

    @Test
    public void testGetComplianceReport() {
        when(trustLedgerRepository.count()).thenReturn(100L);
        when(hitlQueueRepository.countByStatus("pending")).thenReturn(5L);
        when(outboxEventRepository.countByEventTypeAndStatus("security.anomaly", "PENDING"))
                .thenReturn(10L);
        when(outboxEventRepository.countByEventTypeAndStatus("security.anomaly", "ANALYZED"))
                .thenReturn(90L);

        ResponseEntity<Map<String, Object>> response = securityController.getComplianceReport();
        assertEquals(200, response.getStatusCodeValue());

        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("gdpr"));
        assertNotNull(body.get("pciDss"));
        assertNotNull(body.get("auditTrail"));

        Map<String, Object> auditTrail = (Map<String, Object>) body.get("auditTrail");
        assertEquals(100L, auditTrail.get("totalTrustLedgerEvents"));
        assertEquals(10L, auditTrail.get("pendingSecurityAnomalies"));
        assertEquals(90L, auditTrail.get("analyzedSecurityAnomalies"));

        verify(trustLedgerRepository).count();
        verify(hitlQueueRepository).countByStatus("pending");
        verify(outboxEventRepository).countByEventTypeAndStatus("security.anomaly", "PENDING");
    }
}
