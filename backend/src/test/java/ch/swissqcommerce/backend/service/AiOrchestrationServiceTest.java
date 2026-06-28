package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class AiOrchestrationServiceTest {

    @Mock private LlmGatewayPort llmGateway;

    @InjectMocks private AiOrchestrationService aiOrchestrationService;

    @Test
    public void testOrchestrateComplexTask_Success() {
        LlmResponse response = LlmResponse.builder()
                .content("response token")
                .tokenCost(0.001)
                .build();
        when(llmGateway.callLlm("test prompt")).thenReturn(response);

        Flux<String> result = aiOrchestrationService.orchestrateComplexTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("response token", list.get(0));
    }

    @Test
    public void testOrchestrateComplexTask_Failure() {
        when(llmGateway.callLlm("test prompt")).thenThrow(new RuntimeException("Cloud LLM offline"));

        Flux<String> result = aiOrchestrationService.orchestrateComplexTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Error: AI reasoning service unavailable.", list.get(0));
    }

    @Test
    public void testExecuteLocalTask_Success() {
        LlmResponse response = LlmResponse.builder()
                .content("local response")
                .tokenCost(0.0001)
                .build();
        when(llmGateway.callLlm("test prompt")).thenReturn(response);

        Flux<String> result = aiOrchestrationService.executeLocalTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("local response", list.get(0));
    }

    @Test
    public void testExecuteLocalTask_Failure() {
        when(llmGateway.callLlm("test prompt")).thenThrow(new RuntimeException("Local Ollama crashed"));

        Flux<String> result = aiOrchestrationService.executeLocalTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Error: Local AI reasoning service unavailable.", list.get(0));
    }
}
