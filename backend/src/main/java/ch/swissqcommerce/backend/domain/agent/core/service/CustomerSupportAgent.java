package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerSupportAgent {

    private final LlmGatewayPort llmGateway;
    private final LettaMemoryService lettaMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentAnalysis analyze(AgentRequest request) {
        String prompt = "You are a customer support agent. Analyze the customer message: \"" + request.getMessage() + "\".\n" +
                "Decide if you need to look up ORDER_STATUS. If so, identify the customerId or orderId from the query or context.\n" +
                "Return a JSON object with: \n" +
                "  \"reply\": a provisional reply,\n" +
                "  \"confidence\": confidence score (0.0 to 1.0),\n" +
                "  \"tool\": \"ORDER_STATUS\" or null,\n" +
                "  \"tool_argument\": the identified customerId or orderId or null.\n" +
                "Response MUST be a valid JSON only, without any markdown formatting block.";

        double[] cost = new double[1];
        String content = callLlmWithLettaFallback(prompt, request.getConversationId(), cost);
        return parseResponse(content, cost[0]);
    }

    public AgentAnalysis generateFinalResponse(AgentRequest request, String toolResult, double initialCost) {
        String prompt = "You are a customer support agent. The customer asked: \"" + request.getMessage() + "\".\n" +
                "We executed the tool and got this data: \"" + toolResult + "\".\n" +
                "Now, formulate the final reply to the customer.\n" +
                "Return a JSON object with: \n" +
                "  \"reply\": your final helpful reply,\n" +
                "  \"confidence\": confidence score (0.0 to 1.0),\n" +
                "  \"tool\": null,\n" +
                "  \"tool_argument\": null\n" +
                "Response MUST be a valid JSON only, without any markdown formatting block.";

        double[] cost = new double[1];
        String content = callLlmWithLettaFallback(prompt, request.getConversationId(), cost);
        return parseResponse(content, cost[0] + initialCost);
    }

    private String callLlmWithLettaFallback(String prompt, String conversationId, double[] tokenCostOut) {
        try {
            if (lettaMemoryService != null && conversationId != null) {
                String lettaResponse = lettaMemoryService.sendMessage(conversationId, prompt);
                if (lettaResponse != null) {
                    tokenCostOut[0] = 0.0;
                    return lettaResponse;
                }
            }
        } catch (Exception ignored) {}
        LlmResponse response = llmGateway.callLlm(prompt);
        tokenCostOut[0] = response.getTokenCost();
        return response.getContent();
    }

    private AgentAnalysis parseResponse(String rawContent, double cost) {
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
            String reply = (String) map.get("reply");
            Number confidenceNum = (Number) map.get("confidence");
            double confidence = confidenceNum != null ? confidenceNum.doubleValue() : 0.5;
            String tool = (String) map.get("tool");
            String toolArgument = (String) map.get("tool_argument");

            return new AgentAnalysis(reply, confidence, tool, toolArgument, cost);
        } catch (Exception e) {
            return new AgentAnalysis("Unable to process request, passing to a human agent.", 0.0, null, null, cost);
        }
    }

    public static class AgentAnalysis {
        public String reply;
        public double confidence;
        public String tool;
        public String toolArgument;
        public double cost;

        public AgentAnalysis(String reply, double confidence, String tool, String toolArgument, double cost) {
            this.reply = reply;
            this.confidence = confidence;
            this.tool = tool;
            this.toolArgument = toolArgument;
            this.cost = cost;
        }
    }
}
