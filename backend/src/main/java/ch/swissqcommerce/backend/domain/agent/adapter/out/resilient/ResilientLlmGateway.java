package ch.swissqcommerce.backend.domain.agent.adapter.out.resilient;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.kimi.KimiLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.pii.PiiPreScanner;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentBudgetTrackerPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single fail-safe LLM gateway (ADR-007 #2 and #3).
 *
 * <p>Core agents depend only on {@link LlmGatewayPort}; this {@code @Primary} composite is the bean
 * they receive, restoring the hexagonal boundary (ADR-001) — gateway selection and fallback no
 * longer live in {@code core/service}.
 *
 * <h2>Fail-safe fallback order</h2>
 *
 * <ol>
 *   <li><b>Python governance</b> — the only path that may receive ungoverned PII, because it
 *       performs the homelab PII gate itself. Used whenever configured.
 *   <li><b>Gated cloud</b> — if (and only if) a cloud model is configured AND the prompt is
 *       PII-free per {@link PiiPreScanner}. A PII-bearing prompt is NEVER sent to cloud, even when
 *       the governance service is down.
 *   <li><b>Local mock / governance-degraded</b> — when no governed path is available and cloud is
 *       either unconfigured or PII-blocked, return a local/degraded response that routes the
 *       request to HITL downstream.
 * </ol>
 *
 * This is the inversion of the previous behaviour, which failed OPEN to raw cloud by default and on
 * any governance outage.
 */
@Component
@Primary
@Slf4j
public class ResilientLlmGateway implements LlmGatewayPort {

    /**
     * Sentinel emitted when governance is unavailable and the prompt cannot be sent to cloud (PII
     * present). It is intentionally not valid JSON, so the agents' parsers fall through to their
     * low-confidence / human-handoff path and the orchestrator escalates to HITL.
     */
    public static final String GOVERNANCE_DEGRADED =
            "GOVERNANCE_DEGRADED: AI governance is unavailable and the request contains "
                    + "sensitive data that must not leave the homelab. Routing to a human agent.";

    private final PythonGovernanceAdapter pythonGovernanceAdapter;
    private final GovernanceLlmClient governanceLlmClient;
    private final GeminiFreeAdapter geminiFreeAdapter;
    private final KimiLlmAdapter kimiLlmAdapter;
    private final MockLlmAdapter mockLlmAdapter;
    private final PiiPreScanner piiPreScanner;
    private final AgentBudgetTrackerPort agentBudgetTracker;

    public ResilientLlmGateway(
            PythonGovernanceAdapter pythonGovernanceAdapter,
            GovernanceLlmClient governanceLlmClient,
            GeminiFreeAdapter geminiFreeAdapter,
            KimiLlmAdapter kimiLlmAdapter,
            MockLlmAdapter mockLlmAdapter,
            PiiPreScanner piiPreScanner,
            AgentBudgetTrackerPort agentBudgetTracker) {
        this.pythonGovernanceAdapter = pythonGovernanceAdapter;
        this.governanceLlmClient = governanceLlmClient;
        this.geminiFreeAdapter = geminiFreeAdapter;
        this.kimiLlmAdapter = kimiLlmAdapter;
        this.mockLlmAdapter = mockLlmAdapter;
        this.piiPreScanner = piiPreScanner;
        this.agentBudgetTracker = agentBudgetTracker;
    }

    @Override
    public LlmResponse callLlm(String prompt) {
        return callLlm(prompt, null);
    }

    @Override
    public LlmResponse callLlm(String prompt, String sessionId) {
        // 0. Fail-fast budget check
        if (agentBudgetTracker.isBudgetExceeded()) {
            log.warn("Cost budget exceeded — blocking LLM call; returning governance-degraded.");
            return governanceDegraded();
        }

        LlmResponse response = executeCallChain(prompt, sessionId);

        // Track the usage cost in the centralized budget tracker
        if (response != null) {
            agentBudgetTracker.trackUsage(response.getTokenCost());
        }

        return response;
    }

    private LlmResponse executeCallChain(String prompt) {
        return executeCallChain(prompt, null);
    }

    private LlmResponse executeCallChain(String prompt, String sessionId) {
        // 1. Preferred: the governed path. It owns the PII gate, so raw prompts are safe here.
        //    Routed through GovernanceLlmClient so the "governance" circuit breaker actually
        //    applies (AOP can't advise a self-invoked private method). When the breaker is OPEN it
        //    throws CallNotPermittedException, caught below to drop to the next fail-safe tier.
        if (pythonGovernanceAdapter.isConfigured()) {
            try {
                return governanceLlmClient.call(prompt, sessionId);
            } catch (Exception e) {
                log.warn(
                        "Python Governance service unavailable ({}). Applying fail-safe fallback.",
                        e.getMessage());
            }
        }

        // 2. Governance is unconfigured or down. A cloud model may only be used for
        //    PII-free prompts — the Java-side pre-scan is the fail-safe gate.
        boolean isPromptPiiFree = !piiPreScanner.containsPii(prompt);

        if (geminiFreeAdapter.isConfigured()) {
            if (!isPromptPiiFree) {
                log.warn(
                        "Governance degraded and PII detected — blocking Gemini cloud LLM; "
                                + "returning governance-degraded response for HITL escalation.");
                return governanceDegraded();
            }
            log.info(
                    "Governance degraded — routing PII-free prompt to gated Gemini cloud"
                            + " fallback.");
            return geminiFreeAdapter.callLlm(prompt);
        }

        // 3. Fallback to Kimi Moonshot AI (OpenAI compatible cloud model)
        if (kimiLlmAdapter.isConfigured()) {
            if (!isPromptPiiFree) {
                log.warn(
                        "Governance degraded and PII detected — blocking Kimi cloud LLM; "
                                + "returning governance-degraded response for HITL escalation.");
                return governanceDegraded();
            }
            log.info("Governance degraded — routing PII-free prompt to gated Kimi cloud fallback.");
            try {
                return kimiLlmAdapter.callLlm(prompt);
            } catch (Exception e) {
                log.warn(
                        "Kimi Cloud fallback failed ({}). Dropping to local mock.", e.getMessage());
            }
        }

        // 4. No cloud configured: stay local. Mock keeps dev/CI off the cloud entirely.
        log.debug("No governed or cloud gateway available — using local mock.");
        return mockLlmAdapter.callLlm(prompt);
    }

    private LlmResponse governanceDegraded() {
        return LlmResponse.builder().content(GOVERNANCE_DEGRADED).tokenCost(0.0).build();
    }
}
