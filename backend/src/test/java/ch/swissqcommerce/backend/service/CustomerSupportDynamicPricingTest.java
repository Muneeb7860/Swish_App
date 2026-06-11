package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.service.*;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.model.HitlQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerSupportDynamicPricingTest {

    @Mock private LlmGatewayPort llmGateway;
    @Mock private LettaMemoryService lettaMemoryService;
    @Mock private AgentOutPort agentOutPort;
    @Mock private DynamicPricingAgent dynamicPricingAgent;
    @Mock private EventUseCase eventUseCase;

    private CustomerSupportAgent customerSupportAgent;
    private AgentToolExecutor agentToolExecutor;
    private MasterOrchestratorService masterOrchestratorService;

    @BeforeEach
    public void setUp() {
        customerSupportAgent = new CustomerSupportAgent(llmGateway, lettaMemoryService);
        agentToolExecutor = new AgentToolExecutor(agentOutPort, dynamicPricingAgent);

        masterOrchestratorService = new MasterOrchestratorService(
                customerSupportAgent,
                agentToolExecutor,
                agentOutPort,
                eventUseCase,
                mock(B2BProcurementAgent.class),
                mock(ProcurementGuardrailsEngine.class),
                mock(ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort.class),
                mock(ch.swissqcommerce.backend.repository.DarkStoreRepository.class),
                mock(ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort.class),
                mock(ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase.class),
                mock(io.micrometer.core.instrument.MeterRegistry.class),
                mock(ch.swissqcommerce.backend.domain.agent.port.out.NegotiationArchivePort.class)
        );
    }

    @Test
    public void testCustomerSupportDynamicPricingRoutingAndCostAccumulation() {
        AgentRequest request = new AgentRequest("Is there a surcharge right now?", "conv-1", "cust-1");

        // 1. Mock first LLM call (analyze) returning DYNAMIC_PRICING tool
        String analyzeJson = "{\"reply\":\"Let me check current dynamic pricing conditions.\",\"confidence\":0.95,\"tool\":\"DYNAMIC_PRICING\",\"tool_argument\":\"raining=true;ratio=0.8;expiry=2\"}";
        when(llmGateway.callLlm(contains("Analyze the customer message")))
                .thenReturn(new LlmResponse(analyzeJson, 0.05));

        // 2. Mock Dynamic Pricing Agent LLM execution called by AgentToolExecutor
        DynamicPricingAgent.PricingAnalysis pricingAnalysis = new DynamicPricingAgent.PricingAnalysis(
                1.8, 15.0, 0.90, "Raining and low riders", 0.03, false
        );
        when(dynamicPricingAgent.recommendPricing(true, 0.8, 0.0, 2, 0.0))
                .thenReturn(pricingAnalysis);

        // 3. Mock final response LLM call returning completed answer
        String finalJson = "{\"reply\":\"Yes, because it is raining and riders are scarce, a surge of 1.8x applies.\",\"confidence\":0.98,\"tool\":null,\"tool_argument\":null}";
        when(llmGateway.callLlm(contains("formulate the final reply")))
                .thenReturn(new LlmResponse(finalJson, 0.04));

        // Execute orchestrator
        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        assertEquals("Yes, because it is raining and riders are scarce, a surge of 1.8x applies.", response.getReply());
        assertEquals(0.98, response.getConfidenceScore());
        // Verify cost metering: 0.05 (analyze) + 0.03 (dynamic pricing tool) + 0.04 (final response) = 0.12 total
        assertEquals(0.12, response.getTokenCost(), 0.001);
        assertFalse(response.isHitlStatus());

        verify(dynamicPricingAgent, times(1)).recommendPricing(true, 0.8, 0.0, 2, 0.0);
    }

    @Test
    public void testAgentToolExecutorParseGuardsAndExceptionFallback() {
        // Mock recommendPricing to throw an exception
        when(dynamicPricingAgent.recommendPricing(anyBoolean(), anyDouble(), anyDouble(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("Pricing engine failed"));

        // Call tool with completely garbled parameters
        AgentToolExecutor.ToolResult result = agentToolExecutor.executeTool("DYNAMIC_PRICING", "raining=maybe;ratio=invalid;expiry=bad;garbage");

        // Verify result content and cost (0.0 cost on fallback)
        assertNotNull(result);
        assertTrue(result.content.contains("Fallback"));
        assertTrue(result.content.contains("Surge Multiplier: 1.00x")); // defaults rain=false -> surge=1.0x
        assertEquals(0.0, result.cost);

        // Should invoke recommendPricing with defaults: rain=false, ratio=1.0, competitor=0.0, expiry=5, vip=0.0
        verify(dynamicPricingAgent, times(1)).recommendPricing(false, 1.0, 0.0, 5, 0.0);
    }

    @Test
    public void testLettaMalformedNonJsonResponseGracefulFallback() {
        AgentRequest request = new AgentRequest("Show my orders", "conv-letta-error", "cust-1");

        // Mock Letta to return non-JSON plain text
        when(lettaMemoryService.sendMessage(anyString(), anyString()))
                .thenReturn("Hello, this is a plain text non-JSON response from Letta!");

        // Execute orchestrator
        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        // Verify fallback reply when parsing throws exception
        assertEquals("Unable to process request, passing to a human agent.", response.getReply());
        assertEquals(0.0, response.getConfidenceScore());
        assertTrue(response.isHitlStatus());
        assertNotNull(response.getTicketId());

        verify(agentOutPort, times(1)).saveHitlQueue(any(HitlQueue.class));
    }
}
