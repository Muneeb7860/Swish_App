package ch.swissqcommerce.backend.domain.agent.adapter.out.resilient;

import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thin Resilience4j-guarded wrapper around the Python governance LLM call.
 *
 * <p>This lives in its <b>own</b> Spring bean on purpose. Resilience4j's {@link CircuitBreaker} is
 * applied by a Spring AOP proxy, and that proxy does <em>not</em> intercept self-invocations or
 * private methods. The previous design annotated a private method called from within {@link
 * ResilientLlmGateway} itself, so the breaker was silently a no-op — it never opened, never tripped,
 * and {@code resilience4j_circuitbreaker_state{name="governance"}} was never published. Routing the
 * call through this injected bean is what actually arms the breaker.
 *
 * <p>There is intentionally <b>no</b> {@code fallbackMethod}: when the breaker is OPEN, Resilience4j
 * throws {@code CallNotPermittedException}, which {@link ResilientLlmGateway#executeCallChain}
 * already catches and uses to drop to its tiered PII-gated cloud / local-mock fallback. Keeping the
 * fallback decision in one place preserves the documented fail-safe ordering (ADR-007).
 */
@Component
@RequiredArgsConstructor
public class GovernanceLlmClient {

    private final PythonGovernanceAdapter pythonGovernanceAdapter;

    /**
     * Calls the governed path under the "governance" circuit breaker (configured in
     * application.properties). On breaker OPEN this throws {@code CallNotPermittedException} so the
     * caller can fall through to its next tier.
     */
    @CircuitBreaker(name = "governance")
    public LlmResponse call(String prompt) {
        return pythonGovernanceAdapter.callLlm(prompt);
    }
}
