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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterOrchestratorService implements AgentUseCase {


    private final CustomerSupportAgent customerSupportAgent;
    private final AgentToolExecutor agentToolExecutor;
    
    private final AgentOutPort agentOutPort;
    private final EventUseCase eventUseCase;

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
}

