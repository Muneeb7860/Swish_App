package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.core.model.GovernedResponse;
import ch.swissqcommerce.backend.domain.agent.port.out.GovernedAgentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R5 bridge to the Python homelab-ai-governance pipeline (`swish.governance.api.url`).
 *
 * <p>Follows the pipeline's own principle — <b>fail safe, fail local, fail
 * loud</b>: if the governance service is unconfigured or unreachable, the call
 * falls back to local handling rather than failing the request. So the Swish
 * backend (and CI, where the URL is empty) runs fully without the Python service.
 */
@Component
public class GovernanceBridgeAdapter implements GovernedAgentPort {

    private static final Logger log = LoggerFactory.getLogger(GovernanceBridgeAdapter.class);

    private final String governanceUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public GovernanceBridgeAdapter(@Value("${swish.governance.api.url:}") String governanceUrl,
                                   RestTemplateBuilder builder) {
        this.governanceUrl = governanceUrl;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofSeconds(35))
                .build();
    }

    /** Test/seam constructor. */
    public GovernanceBridgeAdapter(String governanceUrl, RestTemplate restTemplate) {
        this.governanceUrl = governanceUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GovernedResponse route(String input, String conversationId) {
        if (governanceUrl == null || governanceUrl.isBlank()) {
            return localFallback("Governance bridge not configured");
        }
        try {
            // Matches the Python bridge contract: POST /api/v1/govern
            // {query, expected_format?, local_only_override}.
            Map<String, Object> req = new HashMap<>();
            req.put("query", input);
            req.put("local_only_override", false);

            Map<String, Object> body = restTemplate.postForObject(governanceUrl + "/api/v1/govern", req, Map.class);
            if (body == null) {
                return localFallback("Empty governance response");
            }
            Map<String, Object> routing = (Map<String, Object>) body.getOrDefault("routing_decision", Map.of());
            return GovernedResponse.builder()
                    .status(String.valueOf(body.getOrDefault("status", "success")))
                    // success → "response"; blocked/failed → "message"
                    .reply(String.valueOf(body.getOrDefault("response", body.getOrDefault("message", ""))))
                    .agentId(String.valueOf(body.getOrDefault("agent_id", "unknown")))
                    .intent(String.valueOf(routing.getOrDefault("intent", "unknown")))
                    .localOnly(Boolean.TRUE.equals(routing.get("local_only")))
                    .routedToGovernance(true)
                    .warnings((List<String>) body.getOrDefault("warnings", List.of()))
                    .build();
        } catch (Exception e) {
            log.warn("Governance bridge call failed ({}); falling back to local handling", e.getMessage());
            return localFallback("Governance service unavailable");
        }
    }

    private GovernedResponse localFallback(String reason) {
        return GovernedResponse.builder()
                .status("local_fallback")
                .agentId("local-fallback")
                .reply(reason + "; request handled locally.")
                .intent("unknown")
                .localOnly(true)
                .routedToGovernance(false)
                .warnings(List.of())
                .build();
    }
}
