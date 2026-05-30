package ch.swissqcommerce.backend.domain.agent.port.out;

public interface LlmGatewayPort {
    LlmResponse callLlm(String prompt);
}
