package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.config.SecurityAudit;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.OnboardingApplicationRepository;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
import ch.swissqcommerce.backend.domain.transaction.core.model.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin domain service for system operations including chaos fault injection/resolution, onboarding
 * gate approvals, HITL queue management, and system health monitoring.
 */
@Service
public class AdminService {

    @Autowired private ChaosFaultLogRepository chaosFaultLogRepository;

    @Autowired private OnboardingApplicationRepository onboardingRepository;

    @Autowired private HitlQueueRepository hitlQueueRepository;

    @Autowired private RiderRepository riderRepository;

    @Autowired private OrderRepository orderRepository;

    @Autowired private CustomerRepository customerRepository;

    @Autowired private InventoryRepository inventoryRepository;

    @Autowired
    private ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerService;

    @Autowired private SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired
    private ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase riderUseCase;

    /**
     * Injects a chaos fault into the system for resilience testing. Supports fault types:
     * LATENCY_SPIKE, STORE_OFFLINE, PAYMENT_GATEWAY_DOWN.
     */
    @SecurityAudit(action = "admin.chaos.inject")
    @Transactional
    public ChaosFaultLog injectFault(String faultType, String details) {
        List<String> validFaults =
                List.of(
                        "LATENCY_SPIKE",
                        "STORE_OFFLINE",
                        "PAYMENT_GATEWAY_DOWN",
                        "RIDER_GPS_DROPOUT",
                        "WHOLESALER_API_TIMEOUT");

        if (!validFaults.contains(faultType.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid fault type. Supported: " + String.join(", ", validFaults));
        }

        ChaosFaultLog fault =
                ChaosFaultLog.builder()
                        .faultType(faultType.toUpperCase())
                        .details(details != null ? details : "Chaos fault injected by admin")
                        .build();

        return chaosFaultLogRepository.save(fault);
    }

    /** Resolves an active chaos fault. */
    @Transactional
    public ChaosFaultLog resolveFault(Integer faultId) {
        ChaosFaultLog fault =
                chaosFaultLogRepository
                        .findById(faultId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Fault not found: " + faultId));

        if (fault.getResolvedAt() != null) {
            throw new IllegalStateException("Fault already resolved.");
        }

        fault.setResolvedAt(OffsetDateTime.now());
        return chaosFaultLogRepository.save(fault);
    }

    /** Returns all active (unresolved) chaos faults. */
    public List<ChaosFaultLog> getActiveFaults() {
        return chaosFaultLogRepository.findByResolvedAtIsNull();
    }

    /**
     * Processes onboarding approval gate for a rider application. Implements F11 3-gate approval:
     * ops -> compliance -> admin.
     */
    @SecurityAudit(action = "admin.onboarding.approve")
    @Transactional
    public Map<String, Object> approveOnboarding(String applicationId, String gate) {
        Map<String, Object> riderResult = riderUseCase.approveOnboarding(applicationId, gate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("gate", gate);
        result.put("approved", true);
        result.put("fullyApproved", riderResult.get("fullyApproved"));
        result.put("opsApproval", riderResult.get("opsApproved"));
        result.put("complianceApproval", riderResult.get("complianceApproved"));
        result.put("adminApproval", riderResult.get("adminApproved"));
        return result;
    }

    /**
     * Returns all pending rider onboarding applications. Maps OnboardingApplicationEntity →
     * OnboardingApplication domain model since JPA cannot auto-project an entity to a different
     * class in derived queries.
     */
    public List<OnboardingApplication> getPendingOnboardingApplications() {
        return onboardingRepository.findByApprovalAdminFalse().stream()
                .map(
                        entity ->
                                OnboardingApplication.builder()
                                        .applicationId(entity.getApplicationId())
                                        .applicantType(entity.getApplicantType())
                                        .name(entity.getName())
                                        .details(entity.getDetails())
                                        .approvalOps(entity.getApprovalOps())
                                        .approvalCompliance(entity.getApprovalCompliance())
                                        .approvalAdmin(entity.getApprovalAdmin())
                                        .createdAt(entity.getCreatedAt())
                                        .build())
                .collect(Collectors.toList());
    }

    /** Returns all pending HITL queue tickets. */
    public List<HitlQueue> getPendingHitlTickets() {
        return hitlQueueRepository.findByStatusOrderByCreatedAtDesc("pending");
    }

    /**
     * Resolves a HITL queue ticket (approve or reject). For refund approvals, processes the refund
     * through the ledger.
     */
    @SecurityAudit(action = "admin.hitl.resolve")
    @Transactional
    public Map<String, Object> resolveHitlTicket(String ticketId, String decision, String reason) {
        HitlQueue ticket =
                hitlQueueRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "HITL ticket not found: " + ticketId));

        if (!"pending".equalsIgnoreCase(ticket.getStatus())) {
            throw new IllegalStateException(
                    "Ticket already resolved. Current: " + ticket.getStatus());
        }

        boolean approved = "approve".equalsIgnoreCase(decision);
        ticket.setStatus(approved ? "approved" : "rejected");
        hitlQueueRepository.save(ticket);

        // If approved refund, process the ledger transaction
        if (approved && "refund_customer".equalsIgnoreCase(ticket.getType())) {
            Customer customer = ticket.getCustomer();
            if (customer != null) {
                // Credit customer wallet, debit system
                List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg>
                        legs =
                                List.of(
                                        new ch.swissqcommerce.backend.domain.transaction.port.in
                                                .LedgerUseCase.LedgerLeg(
                                                "system",
                                                null,
                                                ticket.getAmount(),
                                                BigDecimal.ZERO),
                                        new ch.swissqcommerce.backend.domain.transaction.port.in
                                                .LedgerUseCase.LedgerLeg(
                                                "customer",
                                                customer.getCustomerId(),
                                                BigDecimal.ZERO,
                                                ticket.getAmount()));
                ledgerService.recordTransaction(
                        "REFUND-APPROVED", "HITL approved refund for ticket " + ticketId, legs);
            }
        }

        // If rejected, deduct customer trust score
        if (!approved && ticket.getCustomer() != null) {
            Customer customer = ticket.getCustomer();
            int oldTrust = customer.getTrustScore();
            int newTrust = Math.max(0, oldTrust - 10);
            customer.setTrustScore(newTrust);
            customerRepository.save(customer);

            SecurityTrustLedger audit =
                    SecurityTrustLedger.builder()
                            .actorType("customer")
                            .actorId(customer.getCustomerId())
                            .event("HITL-REFUND-REJECTED")
                            .delta(-10)
                            .currentValue(newTrust)
                            .build();
            trustLedgerRepository.save(audit);
        }

        BigDecimal ticketAmount = BigDecimal.valueOf(1.00);
        if (ticket.getAmount() != null) {
            ticketAmount = ticket.getAmount().max(BigDecimal.valueOf(1.00));
        }

        String resolution = approved ? "APPROVED" : "REJECTED";
        String overrideDescription =
                "HITL resolution " + resolution + " for ticket " + ticketId + ". Reason: " + reason;

        List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg>
                systemLegs =
                        List.of(
                                new ch.swissqcommerce.backend.domain.transaction.port.in
                                        .LedgerUseCase.LedgerLeg(
                                        "system", null, ticketAmount, BigDecimal.ZERO),
                                new ch.swissqcommerce.backend.domain.transaction.port.in
                                        .LedgerUseCase.LedgerLeg(
                                        "system", null, BigDecimal.ZERO, ticketAmount));

        ledgerService.recordTransaction("HITL-OVERRIDE", overrideDescription, systemLegs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticketId);
        result.put("decision", decision);
        result.put("status", ticket.getStatus());
        result.put(
                "message",
                approved
                        ? "Ticket approved. Refund processed."
                        : "Ticket rejected. Reason: " + reason);
        return result;
    }

    /**
     * Returns system health dashboard data. Cached for 30 seconds — dashboard polls this; stale-ok
     * within that window.
     */
    @Cacheable("system-health")
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "OPERATIONAL");
        // Key names must match E2E assertions: pendingOnboardingApplications, activeChaosEvents
        health.put("activeChaosEvents", chaosFaultLogRepository.countByResolvedAtIsNull());
        health.put("pendingHitlTickets", hitlQueueRepository.countByStatus("pending"));
        health.put(
                "pendingOnboardingApplications", onboardingRepository.countByApprovalAdminFalse());
        health.put("totalOrders", orderRepository.count());
        health.put("totalInventoryItems", inventoryRepository.count());
        health.put("totalRiders", riderRepository.count());
        health.put("timestamp", OffsetDateTime.now().toString());
        return health;
    }
}
