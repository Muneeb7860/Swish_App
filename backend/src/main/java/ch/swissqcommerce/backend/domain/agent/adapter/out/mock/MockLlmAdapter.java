package ch.swissqcommerce.backend.domain.agent.adapter.out.mock;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import org.springframework.stereotype.Component;

@Component
public class MockLlmAdapter implements LlmGatewayPort {
    @Override
    public LlmResponse callLlm(String prompt) {
        return new LlmResponse();
    }
}
