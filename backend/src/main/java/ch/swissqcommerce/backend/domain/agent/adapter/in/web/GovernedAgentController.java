package ch.swissqcommerce.backend.domain.agent.adapter.in.web;

import ch.swissqcommerce.backend.domain.agent.core.model.GovernedResponse;
import ch.swissqcommerce.backend.domain.agent.port.out.GovernedAgentPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Routes a request through the homelab AI-governance pipeline (R5). Falls back to
 * local handling when the bridge is unconfigured/unreachable, so the endpoint is
 * always safe to call.
 */
@RestController
@RequestMapping("/api/v1/agent/governance")
@RequiredArgsConstructor
@Tag(name = "Agent Governance", description = "Governed routing via the homelab AI pipeline")
public class GovernedAgentController {

    private final GovernedAgentPort governedAgent;

    public record RouteRequest(String input, String conversationId) {}

    @Operation(summary = "Route a prompt through the governed AI pipeline (PII scan, routing, guardrails)")
    @PostMapping("/route")
    public ResponseEntity<GovernedResponse> route(@RequestBody RouteRequest req) {
        return ResponseEntity.ok(governedAgent.route(req.input(), req.conversationId()));
    }
}
