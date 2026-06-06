package ch.swissqcommerce.backend.domain.agent.adapter.out.mock;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import org.springframework.stereotype.Component;

@Component
public class MockLlmAdapter implements LlmGatewayPort {

    @Override
    public LlmResponse callLlm(String prompt) {
        String json;
        if (prompt.contains("ORDER_STATUS")) {
            json = "{\n" +
                    "  \"reply\": \"Checking order status...\",\n" +
                    "  \"confidence\": 0.95,\n" +
                    "  \"tool\": \"ORDER_STATUS\",\n" +
                    "  \"tool_argument\": \"1\"\n" +
                    "}";
        } else if (prompt.contains("dynamic pricing agent") || prompt.contains("Surge multiplier")) {
            json = "{\n" +
                    "  \"surgeMultiplier\": 1.8,\n" +
                    "  \"discountPercent\": 15.0,\n" +
                    "  \"confidence\": 0.92,\n" +
                    "  \"rationale\": \"High rain and low rider ratio justifies 1.8x surge. Near-expiry items discounted by 15% to clear shelf space.\"\n" +
                    "}";
        } else if (prompt.contains("executed the tool")) {
            json = "{\n" +
                    "  \"reply\": \"Your order #1 has been processed successfully. It is currently in pending state.\",\n" +
                    "  \"confidence\": 0.98\n" +
                    "}";
        } else {
            json = "{\n" +
                    "  \"reply\": \"Hello! How can I help you with your orders today?\",\n" +
                    "  \"confidence\": 0.90,\n" +
                    "  \"tool\": null,\n" +
                    "  \"tool_argument\": null\n" +
                    "}";
        }
        return LlmResponse.builder()
                .content(json)
                .tokenCost(0.00005)
                .build();
    }
}
