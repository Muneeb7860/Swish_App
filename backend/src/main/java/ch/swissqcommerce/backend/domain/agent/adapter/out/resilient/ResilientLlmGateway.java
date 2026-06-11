package ch.swissqcommerce.backend.domain.agent.adapter.out.resilient;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.pii.PiiPreScanner;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single fail-safe LLM gateway (ADR-007 #2 and #3).
 *
 * Core agents depend only on {@link LlmGatewayPort}; this {@code @Primary} composite
 * is the bean they receive, restoring the hexagonal boundary (ADR-001) — gateway
 * selection and fallback no longer live in {@code core/service}.
 *
 * <h2>Fail-safe fallback order</h2>
 * <ol>
 *   <li><b>Python governance</b> — the only path that may receive ungoverned PII,
 *       because it performs the homelab PII gate itself. Used whenever configured.</li>
 *   <li><b>Gated cloud</b> — if (and only if) a cloud model is configured AND the
 *       prompt is PII-free per {@link PiiPreScanner}. A PII-bearing prompt is NEVER
 *       sent to cloud, even when the governance service is down.</li>
 *   <li><b>Local mock / governance-degraded</b> — when no governed path is available
 *       and cloud is either unconfigured or PII-blocked, return a local/degraded
 *       response that routes the request to HITL downstream.</li>
 * </ol>
 *
 * This is the inversion of the previous behaviour, which failed OPEN to raw cloud
 * by default and on any governance outage.
 */
@Component
@Primary
@Slf4j
public class ResilientLlmGateway implements LlmGatewayPort {

    /**
     * Sentinel emitted when governance is unavailable and the prompt cannot be sent
     * to cloud (PII present). It is intentionally not valid JSON, so the agents'
     * parsers fall through to their low-confidence / human-handoff path and the
     * orchestrator escalates to HITL.
     */
    public static final String GOVERNANCE_DEGRADED =
            "GOVERNANCE_DEGRADED: AI governance is unavailable and the request contains "
            + "sensitive data that must not leave the homelab. Routing to a human agent.";

    private final PythonGovernanceAdapter pythonGovernanceAdapter;
    private final GeminiFreeAdapter geminiFreeAdapter;
    private final MockLlmAdapter mockLlmAdapter;
    private final PiiPreScanner piiPreScanner;

    public ResilientLlmGateway(PythonGovernanceAdapter pythonGovernanceAdapter,
                               GeminiFreeAdapter geminiFreeAdapter,
                               MockLlmAdapter mockLlmAdapter,
                               PiiPreScanner piiPreScanner) {
        this.pythonGovernanceAdapter = pythonGovernanceAdapter;
        this.geminiFreeAdapter = geminiFreeAdapter;
        this.mockLlmAdapter = mockLlmAdapter;
        this.piiPreScanner = piiPreScanner;
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        // 1. Preferred: the governed path. It owns the PII gate, so raw prompts are safe here.
        if (pythonGovernanceAdapter.isConfigured()) {
            try {
                return pythonGovernanceAdapter.callLlm(prompt);
            } catch (Exception e) {
                log.warn("Python Governance service unavailable ({}). Applying fail-safe fallback.",
                        e.getMessage());
            }
        }

        // 2. Governance is unconfigured or down. A cloud model may only be used for
        //    PII-free prompts — the Java-side pre-scan is the fail-safe gate.
        if (geminiFreeAdapter.isConfigured()) {
            if (piiPreScanner.containsPii(prompt)) {
                log.warn("Governance degraded and PII detected — blocking cloud LLM; "
                        + "returning governance-degraded response for HITL escalation.");
                return governanceDegraded();
            }
            log.info("Governance degraded — routing PII-free prompt to gated cloud fallback.");
            return geminiFreeAdapter.callLlm(prompt);
        }

        // 3. No cloud configured: stay local. Mock keeps dev/CI off the cloud entirely.
        log.debug("No governed or cloud gateway available — using local mock.");
        return mockLlmAdapter.callLlm(prompt);
    }

    private LlmResponse governanceDegraded() {
        return LlmResponse.builder()
                .content(GOVERNANCE_DEGRADED)
                .tokenCost(0.0)
                .build();
    }
}
