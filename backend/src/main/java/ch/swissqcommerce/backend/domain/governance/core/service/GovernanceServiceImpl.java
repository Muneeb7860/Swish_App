package ch.swissqcommerce.backend.domain.governance.core.service;

import ch.swissqcommerce.backend.config.SecurityAudit;
import ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementWorkflow;
import ch.swissqcommerce.backend.domain.governance.core.model.HitlItem;
import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.governance.port.out.HitlQueuePort;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.exception.ResourceNotFoundException;
import ch.swissqcommerce.backend.exception.TicketAlreadyResolvedException;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GovernanceServiceImpl implements GovernanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(GovernanceServiceImpl.class);

    // Composite-id prefixes that route a unified HITL item back to its source queue.
    private static final String PREFIX_PROCUREMENT = "PA-";
    private static final String PREFIX_AGENT = "AQ-";

    private final ProcurementApprovalPort approvalsPort;
    private final B2BRestockOrderPort restockOrderPort;
    private final TelemetryPort telemetryPort;
    private final HitlQueuePort hitlQueuePort;
    private final SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired private ch.swissqcommerce.backend.gateway.ExecutionGateway executionGateway;

    @Autowired
    private ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository
            agentSuggestionRepo;

    @Autowired
    private ch.swissqcommerce.backend.repository.PolicyDecisionRepository policyDecisionRepo;

    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired(required = false)
    private WorkflowClient workflowClient;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public GovernanceServiceImpl(
            ProcurementApprovalPort approvalsPort,
            B2BRestockOrderPort restockOrderPort,
            TelemetryPort telemetryPort,
            HitlQueuePort hitlQueuePort,
            SecurityTrustLedgerRepository trustLedgerRepository,
            MeterRegistry meterRegistry) {
        this.approvalsPort = approvalsPort;
        this.restockOrderPort = restockOrderPort;
        this.telemetryPort = telemetryPort;
        this.hitlQueuePort = hitlQueuePort;
        this.trustLedgerRepository = trustLedgerRepository;

        // Pending-HITL gauge for the Mission Control dashboard (Epic 2.5 row).
        if (meterRegistry != null) {
            Gauge.builder("hitl.pending.count", this, GovernanceServiceImpl::countPendingHitl)
                    .description(
                            "HITL items awaiting supervisor review (B2B overrides + agent"
                                    + " escalations)")
                    .tag("service", "governance")
                    .register(meterRegistry);
        }
    }

    /** Best-effort count for the gauge — never throws on a scrape if the DB hiccups. */
    private double countPendingHitl() {
        try {
            long procurement =
                    approvalsPort.findAll().stream()
                            .filter(a -> "PENDING".equalsIgnoreCase(a.getStatus()))
                            .count();
            long agent = hitlQueuePort.countByStatus("pending");
            return procurement + agent;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @PostConstruct
    public void initKeyPair() {
        try {
            java.io.File keyDir = new java.io.File("config/keys");
            if (!keyDir.exists()) {
                keyDir.mkdirs();
            }
            java.io.File privKeyFile = new java.io.File(keyDir, "governance_private.key");
            java.io.File pubKeyFile = new java.io.File(keyDir, "governance_public.key");

            if (privKeyFile.exists() && pubKeyFile.exists()) {
                byte[] privKeyBytes = java.nio.file.Files.readAllBytes(privKeyFile.toPath());
                byte[] pubKeyBytes = java.nio.file.Files.readAllBytes(pubKeyFile.toPath());

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privKeyBytes);
                this.privateKey = keyFactory.generatePrivate(privSpec);

                X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubKeyBytes);
                this.publicKey = keyFactory.generatePublic(pubSpec);

                log.info(
                        "GovernanceServiceImpl: Loaded persistent RSA KeyPair successfully from"
                                + " config/keys.");
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();

                java.nio.file.Files.write(privKeyFile.toPath(), this.privateKey.getEncoded());
                java.nio.file.Files.write(pubKeyFile.toPath(), this.publicKey.getEncoded());

                log.info(
                        "GovernanceServiceImpl: Generated and saved new persistent RSA KeyPair in"
                                + " config/keys.");
            }
        } catch (Exception e) {
            log.error(
                    "GovernanceServiceImpl: Failed to initialize/load RSA KeyPair, falling back to"
                            + " in-memory key generation",
                    e);
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                log.warn("GovernanceServiceImpl: Fell back to in-memory transient RSA KeyPair.");
            } catch (Exception ex) {
                log.error(
                        "GovernanceServiceImpl: Fallback in-memory KeyPair generation failed", ex);
            }
        }
    }

    @Override
    public void auditNegotiation(Integer restockOrderId, String wholesalerId, BigDecimal amount) {
        B2BRestockOrder restockOrder =
                restockOrderPort
                        .findById(restockOrderId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Restock order not found"));

        ProcurementApproval approval =
                ProcurementApproval.builder()
                        .restockOrderId(restockOrderId)
                        .wholesalerId(wholesalerId)
                        .amount(amount)
                        .status("PENDING")
                        .build();
        approvalsPort.save(approval);
        log.info(
                "GovernanceServiceImpl: Created pending override request for restock order id={},"
                        + " amount={}",
                restockOrderId,
                amount);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }

    @Override
    @SecurityAudit(action = "governance.override.approve")
    public void approveOverride(Integer approvalId, String operator, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override justification reason is required");
        }

        ProcurementApproval approval =
                approvalsPort
                        .findById(approvalId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Approval ticket not found"));

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new TicketAlreadyResolvedException("Ticket is already " + approval.getStatus());
        }

        boolean signaled = false;
        if (workflowClient != null && approval.getRestockOrderId() != null) {
            try {
                B2BProcurementWorkflow workflow =
                        workflowClient.newWorkflowStub(
                                B2BProcurementWorkflow.class,
                                "restock-order-" + approval.getRestockOrderId());
                workflow.approve(operator, reason);
                signaled = true;
                log.info(
                        "GovernanceServiceImpl: Sent approve signal to Temporal workflow"
                                + " restock-order-{}",
                        approval.getRestockOrderId());
            } catch (Exception e) {
                log.warn(
                        "GovernanceServiceImpl: Failed to signal workflow, falling back to direct"
                                + " db update: {}",
                        e.getMessage());
            }
        }

        if (trustLedgerRepository != null) {
            SecurityTrustLedger audit =
                    SecurityTrustLedger.builder()
                            .actorType("system")
                            .actorId(operator != null ? operator : "admin")
                            .event("HITL-OVERRIDE-HASH:" + sha256(reason))
                            .delta(0)
                            .currentValue(100)
                            .build();
            trustLedgerRepository.save(audit);
        }

        if (!signaled) {
            approval.setStatus("APPROVED");
            approval.setOverrideBy(operator);
            approval.setOverrideReason(reason);
            approvalsPort.save(approval);

            if (approval.getRestockOrderId() != null) {
                restockOrderPort
                        .findById(approval.getRestockOrderId())
                        .ifPresent(
                                order -> {
                                    order.setStatus("fulfilled");
                                    restockOrderPort.save(order);
                                    log.info(
                                            "GovernanceServiceImpl: B2B Restock order id={}"
                                                    + " approved by operator={}.",
                                            order.getRestockOrderId(),
                                            operator);
                                });
            }
        }
    }

    @Override
    @SecurityAudit(action = "governance.override.reject")
    public void rejectOverride(Integer approvalId, String operator, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override justification reason is required");
        }

        ProcurementApproval approval =
                approvalsPort
                        .findById(approvalId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Approval ticket not found"));

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new TicketAlreadyResolvedException("Ticket is already " + approval.getStatus());
        }

        boolean signaled = false;
        if (workflowClient != null && approval.getRestockOrderId() != null) {
            try {
                B2BProcurementWorkflow workflow =
                        workflowClient.newWorkflowStub(
                                B2BProcurementWorkflow.class,
                                "restock-order-" + approval.getRestockOrderId());
                workflow.reject(operator, reason);
                signaled = true;
                log.info(
                        "GovernanceServiceImpl: Sent reject signal to Temporal workflow"
                                + " restock-order-{}",
                        approval.getRestockOrderId());
            } catch (Exception e) {
                log.warn(
                        "GovernanceServiceImpl: Failed to signal workflow, falling back to direct"
                                + " db update: {}",
                        e.getMessage());
            }
        }

        if (trustLedgerRepository != null) {
            SecurityTrustLedger audit =
                    SecurityTrustLedger.builder()
                            .actorType("system")
                            .actorId(operator != null ? operator : "admin")
                            .event("HITL-OVERRIDE-HASH:" + sha256(reason))
                            .delta(0)
                            .currentValue(100)
                            .build();
            trustLedgerRepository.save(audit);
        }

        if (!signaled) {
            approval.setStatus("REJECTED");
            approval.setOverrideBy(operator);
            approval.setOverrideReason(reason);
            approvalsPort.save(approval);

            if (approval.getRestockOrderId() != null) {
                restockOrderPort
                        .findById(approval.getRestockOrderId())
                        .ifPresent(
                                order -> {
                                    order.setStatus("failed");
                                    restockOrderPort.save(order);
                                    log.warn(
                                            "GovernanceServiceImpl: B2B Restock order id={}"
                                                    + " rejected by operator={}.",
                                            order.getRestockOrderId(),
                                            operator);
                                });
            }
        }
    }

    @Override
    @SecurityAudit(action = "governance.override.adjust")
    public void adjustOverride(
            Integer approvalId, double newPrice, String operator, String reason) {
        ProcurementApproval approval =
                approvalsPort
                        .findById(approvalId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Approval ticket not found"));

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new TicketAlreadyResolvedException("Ticket is already " + approval.getStatus());
        }

        boolean signaled = false;
        if (workflowClient != null && approval.getRestockOrderId() != null) {
            try {
                B2BProcurementWorkflow workflow =
                        workflowClient.newWorkflowStub(
                                B2BProcurementWorkflow.class,
                                "restock-order-" + approval.getRestockOrderId());
                workflow.adjustBid(newPrice, operator, reason);
                signaled = true;
                log.info(
                        "GovernanceServiceImpl: Sent adjustBid signal to Temporal workflow"
                                + " restock-order-{} with price={}",
                        approval.getRestockOrderId(),
                        newPrice);
            } catch (Exception e) {
                log.warn(
                        "GovernanceServiceImpl: Failed to signal workflow (adjust), falling back to"
                                + " direct db update: {}",
                        e.getMessage());
            }
        }

        if (trustLedgerRepository != null) {
            SecurityTrustLedger audit =
                    SecurityTrustLedger.builder()
                            .actorType("system")
                            .actorId(operator != null ? operator : "admin")
                            .event("HITL-OVERRIDE-HASH:" + sha256(reason))
                            .delta(0)
                            .currentValue(100)
                            .build();
            trustLedgerRepository.save(audit);
        }

        if (!signaled) {
            approval.setStatus("APPROVED");
            approval.setOverrideBy(operator);
            approval.setOverrideReason("Adjusted bid to " + newPrice + " CHF: " + reason);
            approvalsPort.save(approval);

            if (approval.getRestockOrderId() != null) {
                restockOrderPort
                        .findById(approval.getRestockOrderId())
                        .ifPresent(
                                order -> {
                                    order.setInvoiceAmount(BigDecimal.valueOf(newPrice));
                                    order.setStatus("fulfilled");
                                    restockOrderPort.save(order);
                                    log.info(
                                            "GovernanceServiceImpl: B2B Restock order id={}"
                                                    + " adjusted to price={} and approved by"
                                                    + " operator={}.",
                                            order.getRestockOrderId(),
                                            newPrice,
                                            operator);
                                });
            }
        }
    }

    @Override
    public void adjustHitlItem(
            String compositeId, double newPrice, String operator, String reason) {
        if (compositeId == null) {
            throw new IllegalArgumentException("HITL item id is required");
        }
        if (compositeId.startsWith(PREFIX_PROCUREMENT)) {
            Integer approvalId = parseId(compositeId.substring(PREFIX_PROCUREMENT.length()));
            adjustOverride(approvalId, newPrice, operator, reason);
        } else if (compositeId.chars().allMatch(Character::isDigit)) {
            Integer approvalId = parseId(compositeId);
            adjustOverride(approvalId, newPrice, operator, reason);
        } else {
            throw new IllegalArgumentException(
                    "Adjust action is only supported for B2B Procurement items");
        }
    }

    @Override
    public String signDeliverySummary(String orderId, String podHash) {
        log.info(
                "GovernanceServiceImpl: Generating digital signature for order id={}, podHash={}",
                orderId,
                podHash);
        try {
            int orderIdInt = Integer.parseInt(orderId);
            List<OrderTelemetryLog> logs = telemetryPort.findByOrderId(orderIdInt);
            if (logs.isEmpty()) {
                throw new ResourceNotFoundException("No telemetry logs found for order " + orderId);
            }

            BigDecimal minTemp = BigDecimal.valueOf(999.0);
            BigDecimal maxTemp = BigDecimal.valueOf(-999.0);
            BigDecimal sumTemp = BigDecimal.ZERO;

            for (OrderTelemetryLog tLog : logs) {
                BigDecimal temp = tLog.getTemperature();
                if (temp.compareTo(minTemp) < 0) minTemp = temp;
                if (temp.compareTo(maxTemp) > 0) maxTemp = temp;
                sumTemp = sumTemp.add(temp);
            }

            BigDecimal avgTemp =
                    sumTemp.divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
            String summaryPayload =
                    String.format(
                            "orderId:%s|minTemp:%s|maxTemp:%s|avgTemp:%s|podHash:%s",
                            orderId, minTemp, maxTemp, avgTemp, podHash);

            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(privateKey);
            privateSignature.update(summaryPayload.getBytes(StandardCharsets.UTF_8));
            byte[] signature = privateSignature.sign();

            String encodedSignature = Base64.getEncoder().encodeToString(signature);
            log.info(
                    "GovernanceServiceImpl: Cryptographic signature generated: {}",
                    encodedSignature);
            return encodedSignature;
        } catch (Exception e) {
            log.error(
                    "GovernanceServiceImpl: Failed to sign telemetry summary for order {}",
                    orderId,
                    e);
            throw new RuntimeException("Cryptographic signing failed", e);
        }
    }

    @Override
    public List<ProcurementApproval> getPendingApprovals() {
        return approvalsPort.findAll().stream()
                .filter(a -> "PENDING".equalsIgnoreCase(a.getStatus()))
                .toList();
    }

    // ── Phase 8: unified HITL queue (B2B overrides + agent escalations) ──────────

    @Override
    @Transactional(readOnly = true)
    public List<HitlItem> getPendingHitlItems() {
        List<HitlItem> items = new ArrayList<>();

        approvalsPort.findAll().stream()
                .filter(a -> "PENDING".equalsIgnoreCase(a.getStatus()))
                .forEach(
                        a ->
                                items.add(
                                        HitlItem.builder()
                                                .id(PREFIX_PROCUREMENT + a.getId())
                                                .source("B2B_PROCUREMENT")
                                                .type("b2b_override")
                                                .status(normalize(a.getStatus()))
                                                .amount(a.getAmount())
                                                .description(
                                                        "B2B restock override — wholesaler "
                                                                + a.getWholesalerId()
                                                                + ", restock order "
                                                                + a.getRestockOrderId())
                                                .reference(
                                                        a.getRestockOrderId() == null
                                                                ? null
                                                                : String.valueOf(
                                                                        a.getRestockOrderId()))
                                                .build()));

        hitlQueuePort
                .findByStatus("pending")
                .forEach(
                        t ->
                                items.add(
                                        HitlItem.builder()
                                                .id(PREFIX_AGENT + t.getTicketId())
                                                .source("AGENT_ESCALATION")
                                                .type(t.getType())
                                                .status(normalize(t.getStatus()))
                                                .amount(t.getAmount())
                                                .description(t.getDescription())
                                                .reference(
                                                        t.getOrderId() == null
                                                                ? null
                                                                : String.valueOf(t.getOrderId()))
                                                .createdAt(t.getCreatedAt())
                                                .build()));

        return items;
    }

    @Override
    public void resolveHitlItem(
            String compositeId, boolean approve, String operator, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override justification reason is required");
        }
        if (compositeId == null) {
            throw new IllegalArgumentException("HITL item id is required");
        }
        if (compositeId.startsWith(PREFIX_PROCUREMENT)) {
            Integer approvalId = parseId(compositeId.substring(PREFIX_PROCUREMENT.length()));
            if (approve) approveOverride(approvalId, operator, reason);
            else rejectOverride(approvalId, operator, reason);
        } else if (compositeId.startsWith(PREFIX_AGENT)) {
            resolveAgentEscalation(
                    compositeId.substring(PREFIX_AGENT.length()), approve, operator, reason);
        } else if (compositeId.chars().allMatch(Character::isDigit)) {
            // Legacy bare-numeric id → procurement approval (pre-unification callers).
            Integer approvalId = parseId(compositeId);
            if (approve) approveOverride(approvalId, operator, reason);
            else rejectOverride(approvalId, operator, reason);
        } else {
            throw new IllegalArgumentException("Unknown HITL item id: " + compositeId);
        }
    }

    private void resolveAgentEscalation(
            String ticketId, boolean approve, String operator, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override justification reason is required");
        }
        HitlQueue ticket =
                hitlQueuePort
                        .findByTicketId(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "HITL ticket not found: " + ticketId));

        if (!"pending".equalsIgnoreCase(ticket.getStatus())) {
            throw new TicketAlreadyResolvedException("Ticket is already " + ticket.getStatus());
        }

        String domain = null;
        if (ticket.getType() != null && ticket.getType().startsWith("agent_")) {
            domain = ticket.getType().substring("agent_".length());
        }
        String agentName = null;
        String desc = ticket.getDescription();
        if (desc != null && desc.startsWith("[")) {
            int closingBrace = desc.indexOf("]");
            if (closingBrace > 1) {
                agentName = desc.substring(1, closingBrace);
            }
        }

        if (agentName != null && domain != null) {
            List<ch.swissqcommerce.backend.model.AgentSuggestionEntity> suggestions =
                    agentSuggestionRepo.findByAgentNameAndDomainAndStatusOrderByCreatedAtDesc(
                            agentName, domain, "pending");
            if (!suggestions.isEmpty()) {
                ch.swissqcommerce.backend.model.AgentSuggestionEntity suggestion =
                        suggestions.get(0);
                if (approve) {
                    try {
                        suggestion.setStatus("approved");
                        agentSuggestionRepo.save(suggestion);

                        ch.swissqcommerce.backend.model.PolicyDecision policyDecision =
                                ch.swissqcommerce.backend.model.PolicyDecision.builder()
                                        .suggestion(suggestion)
                                        .decision("approved")
                                        .policyVersion("v1")
                                        .reason(reason)
                                        .decidedBy(
                                                "user:"
                                                        + (operator != null
                                                                ? operator
                                                                : "anonymous"))
                                        .build();
                        policyDecisionRepo.save(policyDecision);

                        executionGateway.execute(suggestion.getId(), operator);
                        log.info(
                                "GovernanceServiceImpl: Executed approved suggestion ID {}",
                                suggestion.getId());
                    } catch (Exception e) {
                        log.error(
                                "GovernanceServiceImpl: Failed to execute approved suggestion", e);
                        throw new RuntimeException(
                                "Execution of approved agent action failed: " + e.getMessage(), e);
                    }
                } else {
                    suggestion.setStatus("rejected");
                    agentSuggestionRepo.save(suggestion);

                    ch.swissqcommerce.backend.model.PolicyDecision policyDecision =
                            ch.swissqcommerce.backend.model.PolicyDecision.builder()
                                    .suggestion(suggestion)
                                    .decision("rejected")
                                    .policyVersion("v1")
                                    .reason(reason)
                                    .decidedBy(
                                            "user:" + (operator != null ? operator : "anonymous"))
                                    .build();
                    policyDecisionRepo.save(policyDecision);
                    log.info(
                            "GovernanceServiceImpl: Suggestion ID {} rejected by operator",
                            suggestion.getId());
                }
            } else {
                log.warn(
                        "GovernanceServiceImpl: No matching pending AgentSuggestionEntity found for"
                                + " agent {} and domain {}",
                        agentName,
                        domain);
                throw new ResourceNotFoundException(
                        "No pending agent suggestion found for agent "
                                + agentName
                                + " and domain "
                                + domain);
            }
        } else {
            log.warn(
                    "GovernanceServiceImpl: Could not parse agentName ({}) or domain ({}) from"
                            + " ticket description: {}",
                    agentName,
                    domain,
                    desc);
        }

        ticket.setStatus(approve ? "approved" : "voided");
        ticket.setDescription(
                ticket.getDescription()
                        + " | "
                        + (approve ? "APPROVED" : "VOIDED")
                        + " by "
                        + operator
                        + ": "
                        + reason);

        if (trustLedgerRepository != null) {
            SecurityTrustLedger audit =
                    SecurityTrustLedger.builder()
                            .actorType("system")
                            .actorId(operator != null ? operator : "admin")
                            .event("HITL-OVERRIDE-HASH:" + sha256(reason))
                            .delta(0)
                            .currentValue(100)
                            .build();
            trustLedgerRepository.save(audit);
        }

        hitlQueuePort.save(ticket);
        log.info(
                "GovernanceServiceImpl: agent-escalation ticket {} {} by operator={}.",
                ticketId,
                approve ? "approved" : "voided",
                operator);
    }

    private Integer parseId(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid procurement approval id: " + raw);
        }
    }

    /** Normalize either queue's status spelling to PENDING / APPROVED / REJECTED. */
    private String normalize(String status) {
        if (status == null) return "PENDING";
        return switch (status.toLowerCase()) {
            case "approved" -> "APPROVED";
            case "rejected", "voided" -> "REJECTED";
            default -> "PENDING";
        };
    }
}
