package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class PythonGovernanceAdapter implements LlmGatewayPort {

    @Value("${swish.governance.api.url:}")
    private String apiUrl;

    private final GeminiFreeAdapter geminiFreeAdapter;
    private final MockLlmAdapter mockLlmAdapter;
    private final RestTemplate restTemplate = new RestTemplate();

    public PythonGovernanceAdapter(GeminiFreeAdapter geminiFreeAdapter, MockLlmAdapter mockLlmAdapter) {
        this.geminiFreeAdapter = geminiFreeAdapter;
        this.mockLlmAdapter = mockLlmAdapter;
    }

    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.trim().isEmpty();
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        if (!isConfigured()) {
            log.debug("Python Governance API URL not configured. Using fallback.");
            return getFallbackGateway().callLlm(prompt);
        }

        String endpointUrl = apiUrl.replaceAll("/+$", "") + "/api/v1/govern";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("query", prompt);

        // If the prompt contains "Response MUST be a valid JSON", request JSON format
        if (prompt.contains("valid JSON") || prompt.contains("structure:")) {
            body.put("expected_format", "json");
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Forwarding prompt to Python Governance service at {}", endpointUrl);
            Map<?, ?> response = restTemplate.postForObject(endpointUrl, entity, Map.class);
            
            if (response != null) {
                String status = (String) response.get("status");
                if ("success".equals(status)) {
                    String content = (String) response.get("response");
                    
                    // Estimate token cost using standard formula
                    double inputTokens = prompt.length() / 4.0;
                    double outputTokens = content != null ? content.length() / 4.0 : 0.0;
                    double cost = (inputTokens * 0.000000075) + (outputTokens * 0.00000030);

                    return LlmResponse.builder()
                            .content(content)
                            .tokenCost(cost)
                            .build();
                } else {
                    String message = (String) response.get("message");
                    log.warn("Python Governance service returned status: {} - message: {}", status, message);
                    
                    return LlmResponse.builder()
                            .content("Governance Blocked/Failed: " + message)
                            .tokenCost(0.0)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Python Governance service at {} is offline or failed. Falling back. Error: {}", endpointUrl, e.getMessage());
            return getFallbackGateway().callLlm(prompt);
        }

        log.warn("Invalid response from Python Governance service. Falling back.");
        return getFallbackGateway().callLlm(prompt);
    }

    private LlmGatewayPort getFallbackGateway() {
        if (geminiFreeAdapter.isConfigured()) {
            return geminiFreeAdapter;
        }
        return mockLlmAdapter;
    }
}
