package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.OnboardingApplicationRepository;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Admin domain service for system operations including
 * chaos fault injection/resolution, onboarding gate approvals,
 * HITL queue management, and system health monitoring.
 */
@Service
public class AdminService {

    @Autowired
    private ChaosFaultLogRepository chaosFaultLogRepository;

    @Autowired
    private OnboardingApplicationRepository onboardingRepository;

    @Autowired
    private HitlQueueRepository hitlQueueRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerService;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    /**
     * Injects a chaos fault into the system for resilience testing.
     * Supports fault types: LATENCY_SPIKE, STORE_OFFLINE, PAYMENT_GATEWAY_DOWN.
     */
    @Transactional
    public ChaosFaultLog injectFault(String faultType, String details) {
        List<String> validFaults = List.of("LATENCY_SPIKE", "STORE_OFFLINE", "PAYMENT_GATEWAY_DOWN",
                "RIDER_GPS_DROPOUT", "WHOLESALER_API_TIMEOUT");

        if (!validFaults.contains(faultType.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid fault type. Supported: " + String.join(", ", validFaults));
        }

        ChaosFaultLog fault = ChaosFaultLog.builder()
                .faultType(faultType.toUpperCase())
                .details(details != null ? details : "Chaos fault injected by admin")
                .build();

        return chaosFaultLogRepository.save(fault);
    }

    /**
     * Resolves an active chaos fault.
     */
    @Transactional
    public ChaosFaultLog resolveFault(Integer faultId) {
        ChaosFaultLog fault = chaosFaultLogRepository.findById(faultId)
                .orElseThrow(() -> new NoSuchElementException("Fault not found: " + faultId));

        if (fault.getResolvedAt() != null) {
            throw new IllegalStateException("Fault already resolved.");
        }

        fault.setResolvedAt(OffsetDateTime.now());
        return chaosFaultLogRepository.save(fault);
    }

    /**
     * Returns all active (unresolved) chaos faults.
     */
    public List<ChaosFaultLog> getActiveFaults() {
        return chaosFaultLogRepository.findByResolvedAtIsNull();
    }

    /**
     * Processes onboarding approval gate for a rider application.
     * Implements F11 3-gate approval: ops -> compliance -> admin.
     */
    @Transactional
    public Map<String, Object> approveOnboarding(String applicationId, String gate) {
        OnboardingApplication app = onboardingRepository.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));

        switch (gate.toLowerCase()) {
            case "ops":
                app.setApprovalOps(true);
                break;
            case "compliance":
                if (!app.getApprovalOps()) {
                    throw new IllegalStateException("Ops approval must precede compliance approval.");
                }
                app.setApprovalCompliance(true);
                break;
            case "admin":
                if (!app.getApprovalOps() || !app.getApprovalCompliance()) {
                    throw new IllegalStateException("Both ops and compliance approvals required before admin gate.");
                }
                app.setApprovalAdmin(true);
                break;
            default:
                throw new IllegalArgumentException("Invalid gate. Must be: ops, compliance, or admin.");
        }

        onboardingRepository.save(app);

        // If all 3 gates passed, activate the rider
        boolean fullyApproved = app.getApprovalOps() && app.getApprovalCompliance() && app.getApprovalAdmin();
        if (fullyApproved && "rider".equalsIgnoreCase(app.getApplicantType())) {
            // Find the rider by matching name and pending status
            riderRepository.findAll().stream()
                    .filter(r -> r.getFullName().equalsIgnoreCase(app.getName()))
                    .filter(r -> "pending_review".equalsIgnoreCase(r.getOnboardingStatus()))
                    .findFirst()
                    .ifPresent(rider -> {
                        rider.setOnboardingStatus("active");
                        riderRepository.save(rider);
                    });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("gate", gate);
        result.put("approved", true);
        result.put("fullyApproved", fullyApproved);
        result.put("opsApproval", app.getApprovalOps());
        result.put("complianceApproval", app.getApprovalCompliance());
        result.put("adminApproval", app.getApprovalAdmin());
        return result;
    }

    /**
     * Returns all pending HITL queue tickets.
     */
    public List<HitlQueue> getPendingHitlTickets() {
        return hitlQueueRepository.findByStatusOrderByCreatedAtDesc("pending");
    }

    /**
     * Resolves a HITL queue ticket (approve or reject).
     * For refund approvals, processes the refund through the ledger.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> resolveHitlTicket(String ticketId, String decision, String reason) {
        HitlQueue ticket = hitlQueueRepository.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("HITL ticket not found: " + ticketId));

        if (!"pending".equalsIgnoreCase(ticket.getStatus())) {
            throw new IllegalStateException("Ticket already resolved. Current: " + ticket.getStatus());
        }

        boolean approved = "approve".equalsIgnoreCase(decision);
        ticket.setStatus(approved ? "approved" : "rejected");
        hitlQueueRepository.save(ticket);

        // If approved refund, process the ledger transaction
        if (approved && "refund_customer".equalsIgnoreCase(ticket.getType())) {
            Customer customer = ticket.getCustomer();
            if (customer != null) {
                // Credit customer wallet, debit system
                List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = List.of(
                        new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, ticket.getAmount(), BigDecimal.ZERO),
                        new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("customer", customer.getCustomerId(),
                                BigDecimal.ZERO, ticket.getAmount())
                );
                ledgerService.recordTransaction("REFUND-APPROVED",
                        "HITL approved refund for ticket " + ticketId, legs);
            }
        }

        // If rejected, deduct customer trust score
        if (!approved && ticket.getCustomer() != null) {
            Customer customer = ticket.getCustomer();
            int oldTrust = customer.getTrustScore();
            int newTrust = Math.max(0, oldTrust - 10);
            customer.setTrustScore(newTrust);
            customerRepository.save(customer);

            SecurityTrustLedger audit = SecurityTrustLedger.builder()
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
        String overrideDescription = "HITL resolution " + resolution + " for ticket " + ticketId + ". Reason: " + reason;

        List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> systemLegs = List.of(
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, ticketAmount, BigDecimal.ZERO),
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, ticketAmount)
        );

        ledgerService.recordTransaction("HITL-OVERRIDE", overrideDescription, systemLegs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticketId);
        result.put("decision", decision);
        result.put("status", ticket.getStatus());
        result.put("message", approved ? "Ticket approved. Refund processed." : "Ticket rejected. Reason: " + reason);
        return result;
    }

    /**
     * Returns system health dashboard data.
     */
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "OPERATIONAL");
        health.put("activeFaults", chaosFaultLogRepository.findByResolvedAtIsNull().size());
        health.put("pendingHitlTickets", hitlQueueRepository.findByStatusOrderByCreatedAtDesc("pending").size());
        health.put("pendingOnboarding", onboardingRepository.findByApprovalAdminFalse().size());
        health.put("totalOrders", orderRepository.count());
        health.put("totalInventoryItems", inventoryRepository.count());
        health.put("totalRiders", riderRepository.count());
        health.put("timestamp", OffsetDateTime.now().toString());
        return health;
    }
}

