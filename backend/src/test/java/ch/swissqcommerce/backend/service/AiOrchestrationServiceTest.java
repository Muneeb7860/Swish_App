package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class AiOrchestrationServiceTest {

    @Mock private OpenAiChatModel cloudChatModel;

    @Mock private OllamaChatModel localChatModel;

    @InjectMocks private AiOrchestrationService aiOrchestrationService;

    @Test
    public void testOrchestrateComplexTask_Success() {
        when(cloudChatModel.stream("test prompt")).thenReturn(Flux.just("response token"));

        Flux<String> result = aiOrchestrationService.orchestrateComplexTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("response token", list.get(0));
    }

    @Test
    public void testOrchestrateComplexTask_Failure() {
        when(cloudChatModel.stream("test prompt"))
                .thenReturn(Flux.error(new RuntimeException("Cloud LLM offline")));

        Flux<String> result = aiOrchestrationService.orchestrateComplexTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Error: Cloud AI reasoning service connection failed.", list.get(0));
    }

    @Test
    public void testExecuteLocalTask_Success() {
        when(localChatModel.stream("test prompt")).thenReturn(Flux.just("local response"));

        Flux<String> result = aiOrchestrationService.executeLocalTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("local response", list.get(0));
    }

    @Test
    public void testExecuteLocalTask_Failure() {
        when(localChatModel.stream("test prompt"))
                .thenReturn(Flux.error(new RuntimeException("Local Ollama crashed")));

        Flux<String> result = aiOrchestrationService.executeLocalTask("test prompt");
        List<String> list = result.collectList().block(Duration.ofSeconds(2));

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Error: Local AI reasoning service connection failed.", list.get(0));
    }
}
