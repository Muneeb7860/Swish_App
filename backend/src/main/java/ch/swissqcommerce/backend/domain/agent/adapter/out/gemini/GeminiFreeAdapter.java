package ch.swissqcommerce.backend.domain.agent.adapter.out.gemini;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import java.util.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiFreeAdapter implements LlmGatewayPort {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public GeminiFreeAdapter(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="
                        + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(part));
        Map<String, Object> body = Map.of("contents", List.of(parts));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            Map response = restTemplate.postForObject(url, entity, Map.class);
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> content = (Map<?, ?>) candidate.get("content");
                List<?> resParts = (List<?>) content.get("parts");
                if (resParts != null && !resParts.isEmpty()) {
                    Map<?, ?> resPart = (Map<?, ?>) resParts.get(0);
                    String text = (String) resPart.get("text");

                    double inputTokens = prompt.length() / 4.0;
                    double outputTokens = text.length() / 4.0;
                    double cost = (inputTokens * 0.000000075) + (outputTokens * 0.00000030);

                    return LlmResponse.builder().content(text).tokenCost(cost).build();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }

        throw new RuntimeException("Invalid response format from Gemini API");
    }
}
