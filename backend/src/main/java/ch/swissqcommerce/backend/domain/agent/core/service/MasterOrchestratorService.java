package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.agent.port.out.NegotiationArchivePort;
import ch.swissqcommerce.backend.domain.agent.core.model.NegotiationEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MasterOrchestratorService implements AgentUseCase {

    private final CustomerSupportAgent customerSupportAgent;
    private final AgentToolExecutor agentToolExecutor;
    private final AgentOutPort agentOutPort;
    private final EventUseCase eventUseCase;

    private final B2BProcurementAgent b2BProcurementAgent;
    private final ProcurementGuardrailsEngine procurementGuardrailsEngine;
    private final WholesalerPort wholesalerPort;
    private final DarkStoreRepository darkStoreRepository;
    private final B2BRestockOrderPort restockOrderPort;
    private final GovernanceUseCase governanceUseCase;
    private final MeterRegistry meterRegistry;
    private final NegotiationArchivePort negotiationArchivePort;
    private final AgentBudgetTracker agentBudgetTracker;

    @org.springframework.beans.factory.annotation.Autowired
    private B2BProcurementActivities b2BProcurementActivities;

    private void trackUsage(double cost) {
        agentBudgetTracker.trackUsage(cost);
    }

    @Override
    public AgentResponse processMessage(AgentRequest request) {
        // Cost-budget guardrail only — rate limiting is the Python layer's responsibility.
        if (agentBudgetTracker.isBudgetExceeded()) {
            String reason = "Daily budget limit of $5 exceeded";
            String ticketId;
            synchronized (this) {
                if (agentBudgetTracker.markDailyBudgetEscalated()) {
                    ticketId = triggerHitl(request, null, null, reason);
                } else {
                    ticketId = "BUDGET-EXCEEDED-ACTIVE";
                }
            }
            
            return AgentResponse.builder()
                    .reply("System limit reached. Your request is routed to a customer support agent.")
                    .confidenceScore(0.0)
                    .tokenCost(0.0)
                    .hitlStatus(true)
                    .ticketId(ticketId)
                    .build();
        }

        CustomerSupportAgent.AgentAnalysis analysis = customerSupportAgent.analyze(request);
        trackUsage(analysis.cost);

        String finalReply = analysis.reply;
        double finalConfidence = analysis.confidence;
        double accumulatedCost = analysis.cost;
        Order order = null;

        if (analysis.tool != null) {
            AgentToolExecutor.ToolResult toolResult = agentToolExecutor.executeTool(analysis.tool, analysis.toolArgument);
            trackUsage(toolResult.cost);
            accumulatedCost += toolResult.cost;
            
            try {
                if (analysis.toolArgument != null) {
                    int orderId = Integer.parseInt(analysis.toolArgument.trim());
                    order = agentOutPort.findOrderById(orderId).orElse(null);
                }
            } catch (NumberFormatException ignored) {}

            CustomerSupportAgent.AgentAnalysis finalAnalysis = customerSupportAgent.generateFinalResponse(request, toolResult.content, accumulatedCost);
            trackUsage(finalAnalysis.cost - accumulatedCost);
            accumulatedCost = finalAnalysis.cost;
            finalReply = finalAnalysis.reply;
            finalConfidence = finalAnalysis.confidence;
        }

        boolean hitlStatus = false;
        String ticketId = null;

        if (finalConfidence < 0.70) {
            hitlStatus = true;
            ticketId = triggerHitl(request, order, finalConfidence, "Low confidence score: " + finalConfidence);
        }

        try {
            String eventPayload = String.format("{\"conversationId\":\"%s\",\"hitl\":%b,\"cost\":%f}",
                    request.getConversationId(), hitlStatus, accumulatedCost);
            eventUseCase.publishEvent("agent.message_processed", eventPayload);
        } catch (Exception ignored) {}

        return AgentResponse.builder()
                .reply(finalReply)
                .confidenceScore(finalConfidence)
                .tokenCost(accumulatedCost)
                .hitlStatus(hitlStatus)
                .ticketId(ticketId)
                .build();
    }

    private String triggerHitl(AgentRequest request, Order order, Double confidence, String reason) {
        String ticketId = "HITL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = agentOutPort.findCustomerById(request.getCustomerId()).orElse(null);
        }
        if (customer == null && order != null) {
            customer = order.getCustomer();
        }

        HitlQueue ticket = HitlQueue.builder()
                .ticketId(ticketId)
                .type("agent_escalation")
                .customer(customer)
                .orderId(order != null ? order.getOrderId() : null)
                .description(String.format("Agent escalation due to: %s. Message: %s", reason, request.getMessage()))
                .amount(order != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .status("pending")
                .build();

        agentOutPort.saveHitlQueue(ticket);

        try {
            eventUseCase.publishEvent("agent.hitl_escalated", String.format("{\"ticketId\":\"%s\",\"reason\":\"%s\"}", ticketId, reason));
        } catch (Exception ignored) {}

        return ticketId;
    }

    @Override
    public AgentMetrics getMetrics() {
        // dailyCost / dailyBudgetLimit: the Java-enforced cost guardrail (ADR-007 #1).
        // hourlyRequestCount / hourlyRequestLimit: observability only — request-rate is
        // enforced by the Python governance layer (mirrors its hourly_request_limit: 100).
        return AgentMetrics.builder()
                .dailyCost(agentBudgetTracker.getDailyCost())
                .hourlyRequestCount(agentBudgetTracker.getHourlyRequestCount())
                .dailyBudgetLimit(5.0)
                .hourlyRequestLimit(100)
                .build();
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MasterOrchestratorService.class);

    @Override
    public NegotiationResponse negotiateProcurement(NegotiationRequest request) {
        List<Wholesaler> activeWholesalers;
        try {
            activeWholesalers = wholesalerPort.findAll();
            if (activeWholesalers == null) {
                activeWholesalers = java.util.Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("MasterOrchestratorService: Failed to retrieve active wholesalers from database", e);
            activeWholesalers = java.util.Collections.emptyList();
        }

        activeWholesalers = activeWholesalers.stream()
                .filter(w -> w != null && Boolean.TRUE.equals(w.getIsActive()))
                .toList();

        if (activeWholesalers.isEmpty()) {
            return new NegotiationResponse(
                    false,
                    "No active wholesalers found.",
                    0.0, 0.0, "N/A", "REJECTED", 0.0
            );
        }

        B2BProcurementAgent.NegotiationAnalysis bestAnalysis = null;
        Wholesaler bestWholesaler = null;

        for (Wholesaler wholesaler : activeWholesalers) {
            B2BProcurementAgent.NegotiationAnalysis analysis;
            if (agentBudgetTracker.isBudgetExceeded()) {
                analysis = new B2BProcurementAgent.NegotiationAnalysis(
                        request.getBasePrice() * 0.90,
                        0.50,
                        "Rule-based fallback (Daily budget limit exceeded)",
                        "COUNTER_OFFER",
                        0.0
                );
            } else {
                try {
                    analysis = b2BProcurementActivities.callLlmNegotiation(
                            request.getItemId(), request.getItemName(), request.getBasePrice(), wholesaler.getName());
                    if (analysis == null) {
                        throw new NullPointerException("Negotiation analysis returned null");
                    }
                } catch (Exception e) {
                    log.warn("MasterOrchestratorService: LLM restock negotiation failed for wholesaler {}. Using default fallback bid. Error: {}", wholesaler.getName(), e.getMessage());
                    // Rule-based fallback: 10% discount, 0.50 confidence, 0.0 token cost
                    analysis = new B2BProcurementAgent.NegotiationAnalysis(
                            request.getBasePrice() * 0.90,
                            0.50,
                            "Rule-based fallback (LLM offline)",
                            "COUNTER_OFFER",
                            0.0
                    );
                }
                trackUsage(analysis.cost);
            }

            if (bestAnalysis == null || analysis.proposedPrice < bestAnalysis.proposedPrice) {
                bestAnalysis = analysis;
                bestWholesaler = wholesaler;
            } else if (analysis.proposedPrice == bestAnalysis.proposedPrice) {
                if (bestWholesaler != null && wholesaler.getTrustScore() > bestWholesaler.getTrustScore()) {
                    bestAnalysis = analysis;
                    bestWholesaler = wholesaler;
                }
            }
        }

        if (bestAnalysis == null || bestWholesaler == null) {
            return new NegotiationResponse(
                    false,
                    "Failed to negotiate with any active wholesalers.",
                    0.0, 0.0, "N/A", "REJECTED", 0.0
            );
        }

        // Now start the durable Temporal B2B procurement workflow for the winning wholesaler
        B2BProcurementAgent.NegotiationAnalysis finalOutcome;
        if (agentBudgetTracker.isBudgetExceeded()) {
            finalOutcome = bestAnalysis;
        } else {
            try {
                finalOutcome = b2BProcurementAgent.negotiateRestock(
                        request.getItemId(), request.getItemName(), request.getBasePrice(), bestWholesaler.getName(), request.getQuantity());
            } catch (Exception e) {
                log.error("MasterOrchestratorService: Temporal workflow execution failed for winner: {}", e.getMessage());
                finalOutcome = bestAnalysis;
            }
        }

        var guardrailResult = procurementGuardrailsEngine.validate(
                finalOutcome.proposedPrice, request.getBasePrice(), request.getQuantity());

        // FR-02: archive the negotiation outcome to the document store (best-effort).
        try {
            negotiationArchivePort.archive(NegotiationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .wholesalerId(bestWholesaler.getWholesalerId())
                    .itemId(request.getItemId())
                    .proposedPrice(BigDecimal.valueOf(finalOutcome.proposedPrice))
                    .quantity(request.getQuantity())
                    .approved(guardrailResult.isApproved())
                    .occurredAt(OffsetDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Negotiation archive failed (non-fatal): {}", e.getMessage());
        }

        String winningMessage = "RFQ AUCTION WINNER: " + bestWholesaler.getName() + " (Bid: " + finalOutcome.proposedPrice + " CHF). " + guardrailResult.getMessage();

        return new NegotiationResponse(
                guardrailResult.isApproved(),
                winningMessage,
                finalOutcome.proposedPrice,
                finalOutcome.confidence,
                finalOutcome.rationale,
                finalOutcome.wholesalerResponse,
                finalOutcome.cost
        );
    }
}
