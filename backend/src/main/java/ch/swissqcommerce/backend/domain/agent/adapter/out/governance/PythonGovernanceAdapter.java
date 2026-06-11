package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure client for the Python AI-governance service ("never leaves the homelab"
 * PII gate, model routing, content guardrails, rate limiting).
 *
 * Per ADR-007 #3 this adapter no longer owns any fallback logic — fallback and
 * gateway selection live in {@link ch.swissqcommerce.backend.domain.agent.adapter.out.resilient.ResilientLlmGateway}.
 * On any transport failure or unusable response this adapter throws so the
 * composite gateway can apply the fail-safe chain (PII-gated). A governance
 * <em>block</em> is a definitive governed decision and is returned as-is — it is
 * never bypassed by falling back to an ungoverned model.
 */
@Component
@Slf4j
public class PythonGovernanceAdapter implements LlmGatewayPort {

    @Value("${swish.governance.api.url:}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public PythonGovernanceAdapter(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.trim().isEmpty();
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        if (!isConfigured()) {
            // Selection is the composite's job; if we get here without a URL it is a wiring bug.
            throw new IllegalStateException("Python Governance API URL is not configured");
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

        log.info("Forwarding prompt to Python Governance service at {}", endpointUrl);
        // A transport failure throws RestClientException here, which propagates to the
        // composite gateway's fail-safe handler. We deliberately do NOT catch it.
        Map<?, ?> response = restTemplate.postForObject(endpointUrl, entity, Map.class);

        if (response == null) {
            throw new IllegalStateException("Null response from Python Governance service at " + endpointUrl);
        }

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
        }

        // Governance block/failure is a definitive governed decision — surface it,
        // never fall back to an ungoverned model.
        String message = (String) response.get("message");
        log.warn("Python Governance service returned status: {} - message: {}", status, message);
        return LlmResponse.builder()
                .content("Governance Blocked/Failed: " + message)
                .tokenCost(0.0)
                .build();
    }
}
