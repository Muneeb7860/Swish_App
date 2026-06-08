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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private double dailyCost = 0.0;
    private int hourlyRequestCount = 0;
    private long lastDailyReset = System.currentTimeMillis();
    private long lastHourlyReset = System.currentTimeMillis();

    private synchronized void trackUsage(double cost) {
        long now = System.currentTimeMillis();
        if (now - lastDailyReset > 24 * 60 * 60 * 1000) {
            dailyCost = 0.0;
            lastDailyReset = now;
        }
        if (now - lastHourlyReset > 60 * 60 * 1000) {
            hourlyRequestCount = 0;
            lastHourlyReset = now;
        }

        dailyCost += cost;
        hourlyRequestCount++;
    }

    @Override
    public AgentResponse processMessage(AgentRequest request) {
        long now = System.currentTimeMillis();
        if (now - lastDailyReset > 24 * 60 * 60 * 1000) {
            dailyCost = 0.0;
            lastDailyReset = now;
        }
        if (now - lastHourlyReset > 60 * 60 * 1000) {
            hourlyRequestCount = 0;
            lastHourlyReset = now;
        }

        if (dailyCost >= 5.0 || hourlyRequestCount >= 100) {
            String reason = dailyCost >= 5.0 ? "Daily budget limit of $5 exceeded" : "Hourly request limit of 100 exceeded";
            String ticketId = triggerHitl(request, null, null, reason);
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
            String toolResult = agentToolExecutor.executeTool(analysis.tool, analysis.toolArgument);
            
            try {
                if (analysis.toolArgument != null) {
                    int orderId = Integer.parseInt(analysis.toolArgument.trim());
                    order = agentOutPort.findOrderById(orderId).orElse(null);
                }
            } catch (NumberFormatException ignored) {}

            CustomerSupportAgent.AgentAnalysis finalAnalysis = customerSupportAgent.generateFinalResponse(request, toolResult, accumulatedCost);
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
                .order(order)
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
        return AgentMetrics.builder()
                .dailyCost(dailyCost)
                .hourlyRequestCount(hourlyRequestCount)
                .dailyBudgetLimit(5.0)
                .hourlyRequestLimit(100)
                .build();
    }

    @Override
    public NegotiationResponse negotiateProcurement(NegotiationRequest request) {
        List<Wholesaler> activeWholesalers = wholesalerPort.findAll().stream()
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
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
            var analysis = b2BProcurementAgent.negotiateRestock(
                    request.getItemId(), request.getItemName(), request.getBasePrice(), wholesaler.getName());
            
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

        var guardrailResult = procurementGuardrailsEngine.validate(
                bestAnalysis.proposedPrice, request.getBasePrice(), request.getQuantity());

        if (!guardrailResult.isApproved()) {
            DarkStore store = darkStoreRepository.findAll().stream().findFirst()
                    .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

            BigDecimal orderAmount = BigDecimal.valueOf(bestAnalysis.proposedPrice * request.getQuantity());

            B2BRestockOrder restockOrder = B2BRestockOrder.builder()
                    .store(store)
                    .wholesaler(bestWholesaler)
                    .invoiceAmount(orderAmount)
                    .status("pending")
                    .idempotencyKey("RESTOCK-" + UUID.randomUUID().toString())
                    .build();

            restockOrder = restockOrderPort.save(restockOrder);

            String wholesalerId = bestWholesaler != null ? bestWholesaler.getWholesalerId() : "WHOLESALER-1";
            governanceUseCase.auditNegotiation(restockOrder.getRestockOrderId(), wholesalerId, orderAmount);
        }

        String winningMessage = "RFQ AUCTION WINNER: " + bestWholesaler.getName() + " (Bid: " + bestAnalysis.proposedPrice + " CHF). " + guardrailResult.getMessage();

        return new NegotiationResponse(
                guardrailResult.isApproved(),
                winningMessage,
                bestAnalysis.proposedPrice,
                bestAnalysis.confidence,
                bestAnalysis.rationale,
                bestAnalysis.wholesalerResponse,
                bestAnalysis.cost
        );
    }
}
