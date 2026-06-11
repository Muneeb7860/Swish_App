package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class B2BProcurementAgent {

    private final PythonGovernanceAdapter pythonGovernanceAdapter;
    private final GeminiFreeAdapter geminiFreeAdapter;
    private final MockLlmAdapter mockLlmAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmGatewayPort getLlmGateway() {
        if (pythonGovernanceAdapter.isConfigured()) {
            return pythonGovernanceAdapter;
        }
        if (geminiFreeAdapter.isConfigured()) {
            return geminiFreeAdapter;
        }
        return mockLlmAdapter;
    }

    public NegotiationAnalysis negotiateRestock(String itemId, String itemName, double basePrice, String wholesalerName) {
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

        LlmResponse response = getLlmGateway().callLlm(prompt);
        return parseResponse(response.getContent(), response.getTokenCost());
    }

    private NegotiationAnalysis parseResponse(String rawContent, double cost) {
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

            return new NegotiationAnalysis(proposedPrice, confidence, rationale, wholesalerResponse, cost);
        } catch (Exception e) {
            return new NegotiationAnalysis(0.0, 0.0, "Unable to parse negotiation response.", "REJECTED", cost);
        }
    }

    public static class NegotiationAnalysis {
        public double proposedPrice;
        public double confidence;
        public String rationale;
        public String wholesalerResponse;
        public double cost;

        public NegotiationAnalysis(double proposedPrice, double confidence, String rationale, String wholesalerResponse, double cost) {
            this.proposedPrice = proposedPrice;
            this.confidence = confidence;
            this.rationale = rationale;
            this.wholesalerResponse = wholesalerResponse;
            this.cost = cost;
        }
    }
}
