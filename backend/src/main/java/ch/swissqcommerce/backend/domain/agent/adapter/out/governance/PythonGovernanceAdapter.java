package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Pure client for the Python AI-governance service ("never leaves the homelab" PII gate, model
 * routing, content guardrails, rate limiting).
 *
 * <p>Per ADR-007 #3 this adapter no longer owns any fallback logic — fallback and gateway selection
 * live in {@link ch.swissqcommerce.backend.domain.agent.adapter.out.resilient.ResilientLlmGateway}.
 * On any transport failure or unusable response this adapter throws so the composite gateway can
 * apply the fail-safe chain (PII-gated). A governance <em>block</em> is a definitive governed
 * decision and is returned as-is — it is never bypassed by falling back to an ungoverned model.
 */
@Component
@Slf4j
public class PythonGovernanceAdapter implements LlmGatewayPort {

    @Value("${swish.governance.api.url:}")
    private String apiUrl;

    // ASI07 inter-agent HMAC identity (GOVERNANCE_SPEC §5, staged rollout).
    // Optional by design: signing activates only when a secret is configured
    // (matches the governance service's GOVERNANCE_REQUIRE_AGENT_SIGNATURE,
    // off by default) — an unconfigured secret means zero behavior change,
    // same as before this feature existed.
    @Value("${swish.governance.agent.id:swish-java-backend}")
    private String agentId;

    @Value("${swish.governance.agent.secret:}")
    private String agentSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonGovernanceAdapter(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate =
                restTemplateBuilder
                        .setConnectTimeout(java.time.Duration.ofSeconds(60))
                        .setReadTimeout(java.time.Duration.ofSeconds(60))
                        .build();
    }

    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.trim().isEmpty();
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        return callLlm(prompt, null);
    }

    @Override
    public LlmResponse callLlm(String prompt, String sessionId) {
        if (!isConfigured()) {
            // Selection is the composite's job; if we get here without a URL it is a wiring bug.
            throw new IllegalStateException("Python Governance API URL is not configured");
        }

        String endpointUrl = apiUrl.replaceAll("/+$", "") + "/api/v1/govern";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("query", prompt);
        if (sessionId != null) {
            body.put("session_id", sessionId);
        }

        // If the prompt contains "Response MUST be a valid JSON", request JSON format
        if (prompt.contains("valid JSON") || prompt.contains("structure:")) {
            body.put("expected_format", "json");
        }

        // Sign AFTER `body` is fully built — the signature must cover exactly
        // what gets sent. Wire order doesn't matter (the server re-parses and
        // re-sorts before verifying), but the key/value SET must match.
        if (agentSecret != null && !agentSecret.isBlank()) {
            Map<String, String> sigHeaders = AgentSignatureUtil.sign(agentId, agentSecret, body);
            sigHeaders.forEach(headers::set);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info(
                "Forwarding prompt to Python Governance service at {} with session {}",
                endpointUrl,
                sessionId);
        // A transport failure throws RestClientException here, which propagates to the
        // composite gateway's fail-safe handler. We deliberately do NOT catch it.
        //
        // EXCEPTION — Phase 4 shed (GOVERNANCE_SPEC §5): when a HIGH-risk request is
        // deliberately shed during guardrail degradation, the service returns HTTP 503 with
        // {"status":"unavailable","shed":true,...}. That is a DEFINITIVE governed refusal, not a
        // transport failure. If we let it propagate, ResilientLlmGateway treats it as an outage
        // and — for PII-free prompts — answers via an UNGOVERNED cloud model, defeating the shed.
        // So we catch it and return it as-is (like a block); only genuine failures propagate.
        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(endpointUrl, entity, Map.class);
        } catch (HttpStatusCodeException e) {
            Map<?, ?> errorBody = parseBodyQuietly(e.getResponseBodyAsString());
            if (isShed(errorBody)) {
                String shedMessage = errorBody == null ? null : (String) errorBody.get("message");
                log.warn(
                        "Python Governance SHED a high-risk request during guardrail degradation"
                                + " (HTTP {}): {}. NOT falling back to an ungoverned model.",
                        e.getStatusCode(),
                        shedMessage);
                return LlmResponse.builder()
                        .content("Governance Unavailable (high-risk request shed): " + shedMessage)
                        .tokenCost(0.0)
                        .build();
            }
            throw e; // genuine 4xx/5xx — propagate to the fail-safe chain
        }

        if (response == null) {
            throw new IllegalStateException(
                    "Null response from Python Governance service at " + endpointUrl);
        }

        String status = (String) response.get("status");
        if ("success".equals(status)) {
            String content = (String) response.get("response");

            // Estimate token cost using standard formula
            double inputTokens = prompt.length() / 4.0;
            double outputTokens = content != null ? content.length() / 4.0 : 0.0;
            double cost = (inputTokens * 0.000000075) + (outputTokens * 0.00000030);

            return LlmResponse.builder().content(content).tokenCost(cost).build();
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

    /** A shed carries {@code shed:true} (preferred) or {@code status:"unavailable"} (defensive). */
    private static boolean isShed(Map<?, ?> body) {
        if (body == null) {
            return false;
        }
        return Boolean.TRUE.equals(body.get("shed")) || "unavailable".equals(body.get("status"));
    }

    private Map<?, ?> parseBodyQuietly(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception ex) {
            log.debug("Could not parse governance error body as JSON: {}", ex.getMessage());
            return null;
        }
    }
}
