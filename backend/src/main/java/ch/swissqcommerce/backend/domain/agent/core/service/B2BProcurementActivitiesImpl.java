package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class B2BProcurementActivitiesImpl implements B2BProcurementActivities {

    private final LlmGatewayPort llmGateway;
    private final LettaMemoryService lettaMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
            String itemId, String itemName, double basePrice, String wholesalerName) {
        
        log.info("Temporal Activity: Executing LLM restock negotiation for item {} from wholesaler {}", itemName, wholesalerName);

        String prompt = "You are a B2B procurement agent for Swish OS. We need to restock Item: \"" + itemName + 
                "\" (ID: \"" + itemId + "\") from wholesaler \"" + wholesalerName + "\".\n" +
                "The base wholesale price listed is " + basePrice + " CHF.\n" +
                "Draft an optimized negotiation bid. Suggest a target price (typically 5% to 15% discount on base) and provide the reasoning (e.g. volume bulk buying, early net-10 payment).\n" +
                "Return a JSON object with: \n" +
                "  \"proposedPrice\": proposed discount price as a number,\n" +
                "  \"confidence\": confidence score (0.0 to 1.0),\n" +
                "  \"rationale\": rationale for the discount bid,\n" +
                "  \"wholesalerResponse\": \"ACCEPTED\" or \"COUNTER_OFFER\" or \"REJECTED\".\n" +
                "Response MUST be a valid JSON only, without any markdown formatting block.";

        String content;
        double cost = 0.0;
        String sessionKey = "procurement-" + itemId + "-" + wholesalerName.replaceAll("[^a-zA-Z0-9_-]", "-");
        try {
            if (lettaMemoryService != null) {
                String lettaResponse = lettaMemoryService.sendMessage(sessionKey, prompt);
                if (lettaResponse != null) {
                    content = lettaResponse;
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

            return new B2BProcurementAgent.NegotiationAnalysis(proposedPrice, confidence, rationale, wholesalerResponse, cost);
        } catch (Exception e) {
            log.error("Temporal Activity: Failed to parse LLM response. Error: {}", e.getMessage());
            return new B2BProcurementAgent.NegotiationAnalysis(0.0, 0.0, "Unable to parse negotiation response.", "REJECTED", cost);
        }
    }
}
