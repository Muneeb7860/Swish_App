package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.governance.adapter.in.web.HitlQueueController;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalEntity;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalRepository;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.security.adapter.in.web.SecurityController;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderEntity;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerEntity;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerRepository;
import org.springframework.security.access.AccessDeniedException;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import ch.swissqcommerce.backend.service.AdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain 8 Hardening — Integration tests verifying that all high-privilege
 * operations write immutable audit trails to the outbox and the security
 * trust ledger, and that the compliance report returns live data.
 */
@SpringBootTest
public class SecurityHardeningIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private GovernanceUseCase governanceUseCase;
    @Autowired private SecurityController securityController;
    @Autowired private HitlQueueController hitlQueueController;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private HitlQueueRepository hitlQueueRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private SecurityTrustLedgerRepository trustLedgerRepository;
    @Autowired private ProcurementApprovalRepository approvalRepository;
    @Autowired private B2BRestockOrderRepository restockOrderRepository;
    @Autowired private WholesalerRepository wholesalerRepository;
    @Autowired private DarkStoreRepository darkStoreRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    private static final String CUSTOMER_ID = "SEC-TEST-CUST-001";
    private static final String WHOLESALER_ID = "SEC-TEST-WHOLE-001";

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);

        // Clean slate — REQUIRES_NEW outbox writes survive tx rollback, so clean manually
        transactionTemplate.executeWithoutResult(status -> {
            outboxEventRepository.deleteAll();
            trustLedgerRepository.deleteAll();
            hitlQueueRepository.deleteAll();
            approvalRepository.deleteAll();
            restockOrderRepository.deleteAll();
            customerRepository.deleteById(CUSTOMER_ID);
        });

        // Unconditional save — JPA merges by PK, so safe to call on every setUp
        transactionTemplate.executeWithoutResult(status ->
            darkStoreRepository.save(DarkStore.builder()
                .storeId("store-sec-1")
                .storeName("Sec Test Hub")
                .address("Teststrasse 1")
                .latitude(BigDecimal.valueOf(47.3769))
                .longitude(BigDecimal.valueOf(8.5417))
                .build())
        );

        transactionTemplate.executeWithoutResult(status ->
            wholesalerRepository.save(WholesalerEntity.builder()
                .wholesalerId(WHOLESALER_ID)
                .name("Security Test Wholesaler")
                .baseInvoiceAmount(new BigDecimal("500.00"))
                .fallbackInvoiceAmount(new BigDecimal("600.00"))
                .build())
        );

        // Seed customer with known trust score
        transactionTemplate.executeWithoutResult(status ->
            customerRepository.save(Customer.builder()
                .customerId(CUSTOMER_ID)
                .fullName("Sec Test User")
                .email("sectest@swish.ch")
                .hashedEmail("sectest_hash_001")
                .walletBalance(new BigDecimal("200.00"))
                .loyaltyPoints(0)
                .build())
        );

        // Authenticate as ADMIN for @SecurityAudit and @PreAuthorize
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "swissadmin", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        transactionTemplate.executeWithoutResult(status -> {
            outboxEventRepository.deleteAll();
            trustLedgerRepository.deleteAll();
            hitlQueueRepository.deleteAll();
            approvalRepository.deleteAll();
            restockOrderRepository.deleteAll();
            customerRepository.deleteById(CUSTOMER_ID);
            darkStoreRepository.deleteById("store-sec-1");
            wholesalerRepository.deleteById(WHOLESALER_ID);
        });
    }

    // ─── HITL tests ──────────────────────────────────────────────────────────

    @Test
    public void testHitlRejectionDeductsCustomerTrustAndWritesLedger() {
        // Arrange
        String ticketId = "TICKET-SEC-001";
        transactionTemplate.executeWithoutResult(status ->
            hitlQueueRepository.save(HitlQueue.builder()
                .ticketId(ticketId)
                .type("refund_customer")
                .customer(customerRepository.findById(CUSTOMER_ID).orElseThrow())
                .description("Suspected fraud refund request")
                .amount(new BigDecimal("45.00"))
                .status("pending")
                .build())
        );

        // Act
        Map<String, Object> result = adminService.resolveHitlTicket(ticketId, "reject", "Fraud confirmed");

        // Assert response
        assertEquals("reject", result.get("decision"));
        assertEquals("rejected", result.get("status"));

        // Assert trust ledger entry written (-10 delta for HITL rejection)
        List<SecurityTrustLedger> ledger =
            trustLedgerRepository.findByActorTypeAndActorIdOrderByTimestampDesc("customer", CUSTOMER_ID);
        assertFalse(ledger.isEmpty(), "Trust ledger must have an entry after HITL rejection");
        SecurityTrustLedger entry = ledger.get(0);
        assertEquals("HITL-REFUND-REJECTED", entry.getEvent());
        assertEquals(-10, entry.getDelta());
        assertEquals(90, entry.getCurrentValue()); // started at 100, -10 = 90

        // Assert @SecurityAudit wrote outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(
            events.stream().anyMatch(e ->
                "admin.hitl.resolve".equals(e.getEventType()) && "PENDING".equals(e.getStatus())),
            "Outbox must contain admin.hitl.resolve PENDING event"
        );
    }

    @Test
    public void testHitlApprovalWritesAuditOutboxEvent() {
        // Arrange
        String ticketId = "TICKET-SEC-002";
        transactionTemplate.executeWithoutResult(status ->
            hitlQueueRepository.save(HitlQueue.builder()
                .ticketId(ticketId)
                .type("refund_customer")
                .customer(customerRepository.findById(CUSTOMER_ID).orElseThrow())
                .description("Legitimate refund request")
                .amount(new BigDecimal("20.00"))
                .status("pending")
                .build())
        );

        // Act
        Map<String, Object> result = adminService.resolveHitlTicket(ticketId, "approve", "Approved by ops");

        // Assert response
        assertEquals("approve", result.get("decision"));
        assertEquals("approved", result.get("status"));

        // Assert @SecurityAudit wrote outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(
            events.stream().anyMatch(e ->
                "admin.hitl.resolve".equals(e.getEventType()) && "PENDING".equals(e.getStatus())),
            "Outbox must contain admin.hitl.resolve PENDING event for approved ticket"
        );

        // Assert no trust ledger deduction (only rejected tickets deduct trust)
        List<SecurityTrustLedger> ledger =
            trustLedgerRepository.findByActorTypeAndActorIdOrderByTimestampDesc("customer", CUSTOMER_ID);
        assertTrue(
            ledger.stream().noneMatch(e -> "HITL-REFUND-REJECTED".equals(e.getEvent())),
            "Approved ticket must not write a HITL-REFUND-REJECTED trust ledger entry"
        );
    }

    // ─── Governance override tests ────────────────────────────────────────────

    @Test
    public void testGovernanceApproveOverrideWritesAuditOutbox() {
        // Arrange: create a restock order + approval ticket
        B2BRestockOrderEntity restockEntity = transactionTemplate.execute(status ->
            restockOrderRepository.save(B2BRestockOrderEntity.builder()
                .wholesaler(wholesalerRepository.findById(WHOLESALER_ID).orElseThrow())
                .store(darkStoreRepository.findById("store-sec-1").orElseThrow())
                .invoiceAmount(new BigDecimal("1000.00"))
                .idempotencyKey("sec-test-restock-001")
                .status("pending")
                .build())
        );
        assertNotNull(restockEntity);

        ProcurementApprovalEntity approval = transactionTemplate.execute(status ->
            approvalRepository.save(ProcurementApprovalEntity.builder()
                .restockOrderId(restockEntity.getRestockOrderId())
                .wholesalerId(WHOLESALER_ID)
                .amount(new BigDecimal("1000.00"))
                .status("PENDING")
                .build())
        );
        assertNotNull(approval);

        // Act
        assertDoesNotThrow(() ->
            governanceUseCase.approveOverride(approval.getId(), "swissadmin", "Budget confirmed"));

        // Assert approval entity status updated
        ProcurementApprovalEntity updated = approvalRepository.findById(approval.getId()).orElseThrow();
        assertEquals("APPROVED", updated.getStatus());
        assertEquals("swissadmin", updated.getOverrideBy());

        // Assert @SecurityAudit wrote outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(
            events.stream().anyMatch(e ->
                "governance.override.approve".equals(e.getEventType()) && "PENDING".equals(e.getStatus())),
            "Outbox must contain governance.override.approve PENDING event"
        );
    }

    @Test
    public void testGovernanceRejectOverrideWritesAuditOutbox() {
        // Arrange
        B2BRestockOrderEntity restockEntity = transactionTemplate.execute(status ->
            restockOrderRepository.save(B2BRestockOrderEntity.builder()
                .wholesaler(wholesalerRepository.findById(WHOLESALER_ID).orElseThrow())
                .store(darkStoreRepository.findById("store-sec-1").orElseThrow())
                .invoiceAmount(new BigDecimal("750.00"))
                .idempotencyKey("sec-test-restock-002")
                .status("pending")
                .build())
        );
        assertNotNull(restockEntity);

        ProcurementApprovalEntity approval = transactionTemplate.execute(status ->
            approvalRepository.save(ProcurementApprovalEntity.builder()
                .restockOrderId(restockEntity.getRestockOrderId())
                .wholesalerId(WHOLESALER_ID)
                .amount(new BigDecimal("750.00"))
                .status("PENDING")
                .build())
        );
        assertNotNull(approval);

        // Act
        assertDoesNotThrow(() ->
            governanceUseCase.rejectOverride(approval.getId(), "swissadmin", "Exceeds budget cap"));

        // Assert approval entity status
        ProcurementApprovalEntity updated = approvalRepository.findById(approval.getId()).orElseThrow();
        assertEquals("REJECTED", updated.getStatus());

        // Assert @SecurityAudit wrote outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(
            events.stream().anyMatch(e ->
                "governance.override.reject".equals(e.getEventType()) && "PENDING".equals(e.getStatus())),
            "Outbox must contain governance.override.reject PENDING event"
        );
    }

    // ─── Compliance report tests ──────────────────────────────────────────────

    @Test
    public void testComplianceReportReturnsLivePendingCounts() {
        // Arrange: seed 2 pending HITL tickets and 1 security.anomaly outbox event
        transactionTemplate.executeWithoutResult(status -> {
            hitlQueueRepository.save(HitlQueue.builder()
                .ticketId("COMP-TICKET-001")
                .type("fraud_audit")
                .description("Compliance test ticket 1")
                .amount(new BigDecimal("10.00"))
                .status("pending")
                .build());
            hitlQueueRepository.save(HitlQueue.builder()
                .ticketId("COMP-TICKET-002")
                .type("fraud_audit")
                .description("Compliance test ticket 2")
                .amount(new BigDecimal("20.00"))
                .status("pending")
                .build());
            outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("SECURITY")
                .aggregateId("swissadmin")
                .eventType("security.anomaly")
                .payload("{\"action\":\"security.anomaly\",\"operator\":\"swissadmin\",\"status\":\"FAILED\"}")
                .status("PENDING")
                .retryCount(0)
                .build());
        });

        // Act
        ResponseEntity<Map<String, Object>> response = securityController.getComplianceReport();

        // Assert HTTP 200
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        // Assert GDPR block shows real pending count
        @SuppressWarnings("unchecked")
        Map<String, Object> gdpr = (Map<String, Object>) body.get("gdpr");
        assertNotNull(gdpr);
        assertEquals(2L, gdpr.get("pendingPurgeRequests"),
            "pendingPurgeRequests must reflect actual pending HITL ticket count");

        // Assert audit trail block shows real anomaly count
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) body.get("auditTrail");
        assertNotNull(audit);
        assertEquals(1L, audit.get("pendingSecurityAnomalies"),
            "pendingSecurityAnomalies must reflect actual PENDING security.anomaly outbox events");
        assertEquals(0L, audit.get("analyzedSecurityAnomalies"),
            "analyzedSecurityAnomalies should be 0 when none have been analyzed yet");
    }

    @Test
    public void testOnboardingApprovalWritesAuditOutbox() {
        // AdminService.approveOnboarding is @SecurityAudit annotated.
        // It delegates to RiderUseCase.approveOnboarding which requires an onboarding application.
        // Verify the @SecurityAudit outbox entry is written even when the underlying call throws
        // (because the aspect writes the anomaly event on exception via REQUIRES_NEW).
        assertThrows(Exception.class, () ->
            adminService.approveOnboarding("NON-EXISTENT-APP-ID", "ops")
        );

        // Exception path writes "security.anomaly" (from SecurityAuditAspect catch block)
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(
            events.stream().anyMatch(e -> "security.anomaly".equals(e.getEventType())),
            "Failed onboarding approval must write a security.anomaly event to outbox"
        );
    }

    @Test
    public void testHitlQueueControllerEndpointsEnforceAdminRole() {
        // Authenticate as a regular CUSTOMER
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "customer-user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
            )
        );

        // Accessing getPendingApprovals should throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
            hitlQueueController.getPendingApprovals()
        );

        // Accessing approve should throw AccessDeniedException
        HitlQueueController.OverrideRequest req = new HitlQueueController.OverrideRequest();
        req.setOperator("operator");
        req.setReason("reason");
        assertThrows(AccessDeniedException.class, () ->
            hitlQueueController.approve(1, req)
        );

        // Accessing reject should throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
            hitlQueueController.reject(1, req)
        );

        // Authenticate as ADMIN
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "swissadmin", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        // Accessing getPendingApprovals as ADMIN should not throw AccessDeniedException
        assertDoesNotThrow(() ->
            hitlQueueController.getPendingApprovals()
        );
    }
}
