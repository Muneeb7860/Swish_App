package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI orchestration facade — all calls are routed through {@link LlmGatewayPort} so they
 * automatically benefit from the ADR-007 fail-safe chain: Python governance (PII gate) → Gemini
 * (cloud, PII-free only) → Kimi → Mock → HITL.
 *
 * <p>Previously this service called {@code OpenAiChatModel} and {@code OllamaChatModel} directly,
 * bypassing the PII gate entirely. That gap is now closed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiOrchestrationService {

    /**
     * Injected with the {@code @Primary} {@link
     * ch.swissqcommerce.backend.domain.agent.adapter.out.resilient.ResilientLlmGateway}.
     */
    private final LlmGatewayPort llmGateway;

    /**
     * Route a complex reasoning task through the governed LLM gateway. Returns a single-element
     * Flux for SSE-compatible streaming callers.
     */
    public Flux<String> orchestrateComplexTask(String promptText) {
        try {
            LlmResponse response = llmGateway.callLlm(promptText);
            return Flux.just(response.getContent() != null ? response.getContent() : "");
        } catch (Exception e) {
            log.error("AiOrchestrationService: orchestrateComplexTask failed: {}", e.getMessage());
            return Flux.just("Error: AI reasoning service unavailable.");
        }
    }

    /**
     * Route a local/lightweight task through the governed LLM gateway. The gateway's fallback chain
     * will prefer the homelab governance service (which may route to a local model) before touching
     * cloud providers.
     */
    public Flux<String> executeLocalTask(String promptText) {
        try {
            LlmResponse response = llmGateway.callLlm(promptText);
            return Flux.just(response.getContent() != null ? response.getContent() : "");
        } catch (Exception e) {
            log.error("AiOrchestrationService: executeLocalTask failed: {}", e.getMessage());
            return Flux.just("Error: Local AI reasoning service unavailable.");
        }
    }
}
