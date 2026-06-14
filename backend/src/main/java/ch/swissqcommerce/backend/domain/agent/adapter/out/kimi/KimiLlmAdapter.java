package ch.swissqcommerce.backend.domain.agent.adapter.out.kimi;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KimiLlmAdapter implements LlmGatewayPort {

    @Value("${swish.kimi.api.key:}")
    private String apiKey;

    @Value("${swish.kimi.api.url:https://api.moonshot.ai/v1}")
    private String apiUrl;

    @Value("${swish.kimi.model:moonshot-v1-8k}")
    private String modelName;

    private final RestTemplate restTemplate;

    public KimiLlmAdapter(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Kimi API key is not configured");
        }

        String url = apiUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey.trim());

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = Map.of("model", modelName, "messages", List.of(message));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            Map response = restTemplate.postForObject(url, entity, Map.class);
            List<?> choices = (List<?>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> messageObj = (Map<?, ?>) choice.get("message");
                if (messageObj != null) {
                    String text = (String) messageObj.get("content");

                    // Compute approximate token cost based on character counts
                    double inputTokens = prompt.length() / 4.0;
                    double outputTokens = text != null ? text.length() / 4.0 : 0.0;
                    // Kimi (Moonshot 8k) pricing: ~$0.0016 / 1K tokens input and output
                    double cost = ((inputTokens + outputTokens) / 1000.0) * 0.0016;

                    return LlmResponse.builder().content(text).tokenCost(cost).build();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error calling Kimi API: " + e.getMessage(), e);
        }

        throw new RuntimeException("Invalid response format from Kimi API");
    }
}
