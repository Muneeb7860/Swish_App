package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.exception.ResourceNotFoundException;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class B2BProcurementActivitiesImpl implements B2BProcurementActivities {

    private final LlmGatewayPort llmGateway;
    private final LettaMemoryService lettaMemoryService;
    private final ProcurementGuardrailsEngine guardrailsEngine;
    private final WholesalerPort wholesalerPort;
    private final DarkStoreRepository darkStoreRepository;
    private final B2BRestockOrderPort restockOrderPort;
    private final ProcurementApprovalPort approvalsPort;
    private final AgentBudgetTracker budgetTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
            String itemId, String itemName, double basePrice, String wholesalerName) {

        log.info(
                "Temporal Activity: Executing LLM restock negotiation for item {} from wholesaler"
                        + " {}",
                itemName,
                wholesalerName);

        if (budgetTracker.isBudgetExceeded()) {
            log.warn(
                    "Temporal Activity: Cost budget exceeded ($5 limit reached). Bypassing LLM"
                            + " negotiation for wholesaler {}.",
                    wholesalerName);
            return new B2BProcurementAgent.NegotiationAnalysis(
                    basePrice * 0.90,
                    0.50,
                    "Rule-based fallback (Daily budget limit exceeded)",
                    "COUNTER_OFFER",
                    0.0);
        }

        String prompt =
                "You are a B2B procurement agent for Swish OS. We need to restock Item: \""
                        + itemName
                        + "\" (ID: \""
                        + itemId
                        + "\") from wholesaler \""
                        + wholesalerName
                        + "\".\n"
                        + "The base wholesale price listed is "
                        + basePrice
                        + " CHF.\n"
                        + "Draft an optimized negotiation bid. Suggest a target price (typically 5%"
                        + " to 15% discount on base) and provide the reasoning (e.g. volume bulk"
                        + " buying, early net-10 payment).\n"
                        + "Return a JSON object with: \n"
                        + "  \"proposedPrice\": proposed discount price as a number,\n"
                        + "  \"confidence\": confidence score (0.0 to 1.0),\n"
                        + "  \"rationale\": rationale for the discount bid,\n"
                        + "  \"wholesalerResponse\": \"ACCEPTED\" or \"COUNTER_OFFER\" or"
                        + " \"REJECTED\".\n"
                        + "Response MUST be a valid JSON only, without any markdown formatting"
                        + " block.";

        String content;
        double cost = 0.0;
        String sessionKey =
                "procurement-" + itemId + "-" + wholesalerName.replaceAll("[^a-zA-Z0-9_-]", "-");
        try {
            if (lettaMemoryService != null) {
                String lettaResponse = lettaMemoryService.sendMessage(sessionKey, prompt);
                if (lettaResponse != null) {
                    content = lettaResponse;
                    cost = 0.035; // Default cost estimate for Letta calls to prevent budget bypass
                    budgetTracker.trackUsage(cost); // Track the Letta call cost dynamically
                } else {
                    LlmResponse response = llmGateway.callLlm(prompt);
                    content = response.getContent();
                    cost = response.getTokenCost();
                }
            } else {
                LlmResponse response = llmGateway.callLlm(prompt);
                content = response.getContent();
                cost = response.getTokenCost();
            }
        } catch (Exception e) {
            LlmResponse response = llmGateway.callLlm(prompt);
            content = response.getContent();
            cost = response.getTokenCost();
        }

        return parseResponse(content, cost);
    }

    private B2BProcurementAgent.NegotiationAnalysis parseResponse(String rawContent, double cost) {
        try {
            String json = rawContent.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            }
            if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();

            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            Number proposedPriceNum = (Number) map.get("proposedPrice");
            double proposedPrice = proposedPriceNum != null ? proposedPriceNum.doubleValue() : 0.0;
            Number confidenceNum = (Number) map.get("confidence");
            double confidence = confidenceNum != null ? confidenceNum.doubleValue() : 0.5;
            String rationale = (String) map.get("rationale");
            String wholesalerResponse = (String) map.get("wholesalerResponse");

            return new B2BProcurementAgent.NegotiationAnalysis(
                    proposedPrice, confidence, rationale, wholesalerResponse, cost);
        } catch (Exception e) {
            log.error("Temporal Activity: Failed to parse LLM response. Error: {}", e.getMessage());
            return new B2BProcurementAgent.NegotiationAnalysis(
                    0.0, 0.0, "Unable to parse negotiation response.", "REJECTED", cost);
        }
    }

    @Override
    public boolean checkGuardrail(double proposedPrice, double basePrice, int quantity) {
        log.info("checkGuardrail: price={}, base={}, qty={}", proposedPrice, basePrice, quantity);
        return guardrailsEngine.validate(proposedPrice, basePrice, quantity).isApproved();
    }

    private Integer getRestockOrderIdFromWorkflow() {
        try {
            String workflowId =
                    io.temporal.activity.Activity.getExecutionContext().getInfo().getWorkflowId();
            if (workflowId.startsWith("restock-order-")) {
                return Integer.parseInt(workflowId.substring("restock-order-".length()));
            }
        } catch (Exception e) {
            log.warn("Could not resolve restockOrderId from workflowId: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public int createPendingOrder(
            String itemId, String wholesalerName, double proposedPrice, int quantity) {
        log.info(
                "createPendingOrder: item={}, wholesaler={}, price={}, qty={}",
                itemId,
                wholesalerName,
                proposedPrice,
                quantity);
        Integer orderId = getRestockOrderIdFromWorkflow();
        B2BRestockOrder order = null;
        if (orderId != null) {
            order = restockOrderPort.findById(orderId).orElse(null);
        }
        BigDecimal orderAmount = BigDecimal.valueOf(proposedPrice * quantity);

        if (order != null) {
            order.setInvoiceAmount(orderAmount);
            order.setStatus("pending");
            restockOrderPort.save(order);
            return order.getRestockOrderId();
        }

        DarkStore store =
                darkStoreRepository.findAll().stream()
                        .findFirst()
                        .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

        Wholesaler wholesaler =
                wholesalerPort.findAll().stream()
                        .filter(
                                w ->
                                        w.getName() != null
                                                && w.getName().equalsIgnoreCase(wholesalerName))
                        .findFirst()
                        .orElseGet(
                                () -> wholesalerPort.findAll().stream().findFirst().orElse(null));

        B2BRestockOrder restockOrder =
                B2BRestockOrder.builder()
                        .store(store)
                        .wholesaler(wholesaler)
                        .invoiceAmount(orderAmount)
                        .status("pending")
                        .idempotencyKey("RESTOCK-" + UUID.randomUUID().toString())
                        .build();

        restockOrder = restockOrderPort.save(restockOrder);
        return restockOrder.getRestockOrderId();
    }

    @Override
    public int createFulfilledOrder(
            String itemId, String wholesalerName, double proposedPrice, int quantity) {
        log.info(
                "createFulfilledOrder: item={}, wholesaler={}, price={}, qty={}",
                itemId,
                wholesalerName,
                proposedPrice,
                quantity);
        Integer orderId = getRestockOrderIdFromWorkflow();
        B2BRestockOrder order = null;
        if (orderId != null) {
            order = restockOrderPort.findById(orderId).orElse(null);
        }
        BigDecimal orderAmount = BigDecimal.valueOf(proposedPrice * quantity);

        if (order != null) {
            order.setInvoiceAmount(orderAmount);
            order.setStatus("fulfilled");
            restockOrderPort.save(order);
            return order.getRestockOrderId();
        }

        DarkStore store =
                darkStoreRepository.findAll().stream()
                        .findFirst()
                        .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

        Wholesaler wholesaler =
                wholesalerPort.findAll().stream()
                        .filter(
                                w ->
                                        w.getName() != null
                                                && w.getName().equalsIgnoreCase(wholesalerName))
                        .findFirst()
                        .orElseGet(
                                () -> wholesalerPort.findAll().stream().findFirst().orElse(null));

        B2BRestockOrder restockOrder =
                B2BRestockOrder.builder()
                        .store(store)
                        .wholesaler(wholesaler)
                        .invoiceAmount(orderAmount)
                        .status("fulfilled")
                        .idempotencyKey("RESTOCK-" + UUID.randomUUID().toString())
                        .build();

        restockOrder = restockOrderPort.save(restockOrder);
        return restockOrder.getRestockOrderId();
    }

    @Override
    public void auditNegotiation(
            int restockOrderId, String wholesalerName, double proposedPrice, int quantity) {
        log.info(
                "auditNegotiation: orderId={}, wholesaler={}, price={}, qty={}",
                restockOrderId,
                wholesalerName,
                proposedPrice,
                quantity);

        Wholesaler wholesaler =
                wholesalerPort.findAll().stream()
                        .filter(
                                w ->
                                        w.getName() != null
                                                && w.getName().equalsIgnoreCase(wholesalerName))
                        .findFirst()
                        .orElseGet(
                                () -> wholesalerPort.findAll().stream().findFirst().orElse(null));

        String wholesalerId =
                (wholesaler != null && wholesaler.getWholesalerId() != null)
                        ? wholesaler.getWholesalerId()
                        : "WHOLESALER-1";
        BigDecimal amount = BigDecimal.valueOf(proposedPrice * quantity);

        ProcurementApproval approval =
                ProcurementApproval.builder()
                        .restockOrderId(restockOrderId)
                        .wholesalerId(wholesalerId)
                        .amount(amount)
                        .status("PENDING")
                        .build();
        approvalsPort.save(approval);
    }

    @Override
    public void updateOrderStatus(int restockOrderId, String status) {
        log.info("updateOrderStatus: orderId={}, status={}", restockOrderId, status);
        B2BRestockOrder order =
                restockOrderPort
                        .findById(restockOrderId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Restock order not found"));
        order.setStatus(status);
        restockOrderPort.save(order);
    }

    @Override
    public void updateOrderPrice(int restockOrderId, double newPrice) {
        log.info("updateOrderPrice: orderId={}, newPrice={}", restockOrderId, newPrice);
        B2BRestockOrder order =
                restockOrderPort
                        .findById(restockOrderId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Restock order not found"));
        order.setInvoiceAmount(BigDecimal.valueOf(newPrice));
        restockOrderPort.save(order);
    }

    @Override
    public void updateApprovalStatus(
            int restockOrderId, String status, String operator, String reason) {
        log.info(
                "updateApprovalStatus: orderId={}, status={}, operator={}, reason={}",
                restockOrderId,
                status,
                operator,
                reason);
        ProcurementApproval approval =
                approvalsPort.findAll().stream()
                        .filter(
                                a ->
                                        a.getRestockOrderId() != null
                                                && a.getRestockOrderId().equals(restockOrderId))
                        .findFirst()
                        .orElse(null);

        if (approval != null) {
            approval.setStatus(status);
            approval.setOverrideBy(operator);
            approval.setOverrideReason(reason);
            approvalsPort.save(approval);
        }
    }
}
