package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.service.*;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
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

@ExtendWith(MockitoExtension.class)
public class CustomerSupportDynamicPricingTest {

    @Mock private LlmGatewayPort llmGateway;
    @Mock private LettaMemoryService lettaMemoryService;
    @Mock private AgentOutPort agentOutPort;
    @Mock private DynamicPricingAgent dynamicPricingAgent;
    @Mock private EventUseCase eventUseCase;
    @Mock private B2BProcurementAgent b2BProcurementAgent;
    @Mock private B2BProcurementActivities b2BProcurementActivities;
    @Mock private ProcurementGuardrailsEngine procurementGuardrailsEngine;

    @Mock
    private ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort wholesalerPort;

    private CustomerSupportAgent customerSupportAgent;
    private AgentToolExecutor agentToolExecutor;
    private MasterOrchestratorService masterOrchestratorService;
    private AgentBudgetTracker agentBudgetTracker;

    @BeforeEach
    public void setUp() {
        agentBudgetTracker =
                new AgentBudgetTracker(mock(io.micrometer.core.instrument.MeterRegistry.class));
        customerSupportAgent =
                new CustomerSupportAgent(llmGateway, lettaMemoryService, agentBudgetTracker);
        agentToolExecutor = new AgentToolExecutor(agentOutPort, dynamicPricingAgent);

        masterOrchestratorService =
                new MasterOrchestratorService(
                        customerSupportAgent,
                        agentToolExecutor,
                        agentOutPort,
                        eventUseCase,
                        b2BProcurementAgent,
                        procurementGuardrailsEngine,
                        wholesalerPort,
                        mock(ch.swissqcommerce.backend.repository.DarkStoreRepository.class),
                        mock(
                                ch.swissqcommerce.backend.domain.wholesaler.port.out
                                        .B2BRestockOrderPort.class),
                        mock(
                                ch.swissqcommerce.backend.domain.governance.port.in
                                        .GovernanceUseCase.class),
                        mock(io.micrometer.core.instrument.MeterRegistry.class),
                        mock(
                                ch.swissqcommerce.backend.domain.agent.port.out
                                        .NegotiationArchivePort.class),
                        agentBudgetTracker);

        org.springframework.test.util.ReflectionTestUtils.setField(
                masterOrchestratorService, "b2BProcurementActivities", b2BProcurementActivities);
    }

    @Test
    public void testCustomerSupportDynamicPricingRoutingAndCostAccumulation() {
        AgentRequest request =
                new AgentRequest("Is there a surcharge right now?", "conv-1", "cust-1");

        // 1. Mock first LLM call (analyze) returning DYNAMIC_PRICING tool
        String analyzeJson =
                "{\"reply\":\"Let me check current dynamic pricing"
                    + " conditions.\",\"confidence\":0.95,\"tool\":\"DYNAMIC_PRICING\",\"tool_argument\":\"raining=true;ratio=0.8;expiry=2\"}";
        when(llmGateway.callLlm(contains("Analyze the customer message"), anyString()))
                .thenReturn(new LlmResponse(analyzeJson, 0.05));

        // 2. Mock Dynamic Pricing Agent LLM execution called by AgentToolExecutor
        DynamicPricingAgent.PricingAnalysis pricingAnalysis =
                new DynamicPricingAgent.PricingAnalysis(
                        1.8, 15.0, 0.90, "Raining and low riders", 0.03, false);
        when(dynamicPricingAgent.recommendPricing(true, 0.8, 0.0, 2, 0.0))
                .thenReturn(pricingAnalysis);

        // 3. Mock final response LLM call returning completed answer
        String finalJson =
                "{\"reply\":\"Yes, because it is raining and riders are scarce, a surge of 1.8x"
                        + " applies.\",\"confidence\":0.98,\"tool\":null,\"tool_argument\":null}";
        when(llmGateway.callLlm(contains("formulate the final reply"), anyString()))
                .thenReturn(new LlmResponse(finalJson, 0.04));

        // Execute orchestrator
        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        assertEquals(
                "Yes, because it is raining and riders are scarce, a surge of 1.8x applies.",
                response.getReply());
        assertEquals(0.98, response.getConfidenceScore());
        // Verify cost metering: 0.05 (analyze) + 0.03 (dynamic pricing tool) + 0.04 (final
        // response) = 0.12 total
        assertEquals(0.12, response.getTokenCost(), 0.001);
        assertFalse(response.isHitlStatus());

        verify(dynamicPricingAgent, times(1)).recommendPricing(true, 0.8, 0.0, 2, 0.0);
    }

    @Test
    public void testAgentToolExecutorParseGuardsAndExceptionFallback() {
        // Mock recommendPricing to throw an exception
        when(dynamicPricingAgent.recommendPricing(
                        anyBoolean(), anyDouble(), anyDouble(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("Pricing engine failed"));

        // Call tool with completely garbled parameters
        AgentToolExecutor.ToolResult result =
                agentToolExecutor.executeTool(
                        "DYNAMIC_PRICING", "raining=maybe;ratio=invalid;expiry=bad;garbage");

        // Verify result content and cost (0.0 cost on fallback)
        assertNotNull(result);
        assertTrue(result.content.contains("Fallback"));
        assertTrue(
                result.content.contains(
                        "Surge Multiplier: 1.00x")); // defaults rain=false -> surge=1.0x
        assertEquals(0.0, result.cost);

        // Should invoke recommendPricing with defaults: rain=false, ratio=1.0, competitor=0.0,
        // expiry=5, vip=0.0
        verify(dynamicPricingAgent, times(1)).recommendPricing(false, 1.0, 0.0, 5, 0.0);
    }

    @Test
    public void testLettaMalformedNonJsonResponseGracefulFallback() {
        AgentRequest request = new AgentRequest("Show my orders", "conv-letta-error", "cust-1");

        // Mock LLM call to return non-JSON plain text
        when(llmGateway.callLlm(anyString(), eq("conv-letta-error")))
                .thenReturn(
                        new LlmResponse(
                                "Hello, this is a plain text non-JSON response from Letta!",
                                0.035));

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

    @Test
    public void testLettaMemoryCallAccumulatesEstimatedCost() {
        AgentRequest request = new AgentRequest("Help me", "conv-letta", "cust-1");

        // Mock LLM call to return valid JSON
        String lettaJson =
                "{\"reply\":\"Hello from"
                        + " Letta!\",\"confidence\":0.9,\"tool\":null,\"tool_argument\":null}";
        when(llmGateway.callLlm(anyString(), eq("conv-letta")))
                .thenReturn(new LlmResponse(lettaJson, 0.035));

        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        assertEquals("Hello from Letta!", response.getReply());
        // Verify cost is mapped to the default Letta cost 0.035
        assertEquals(0.035, response.getTokenCost(), 0.001);
    }

    @Test
    public void testDailyBudgetLimitExceededOnlyTriggersSingleHitl() {
        AgentRequest request = new AgentRequest("Check cost limit", "conv-budget", "cust-1");

        // 1. First request succeeds but costs $6.0, exceeding daily budget
        String responseJson =
                "{\"reply\":\"Okay\",\"confidence\":0.9,\"tool\":null,\"tool_argument\":null}";
        when(llmGateway.callLlm(anyString(), anyString()))
                .thenReturn(new LlmResponse(responseJson, 6.0));

        AgentResponse response1 = masterOrchestratorService.processMessage(request);
        assertNotNull(response1);
        assertEquals(6.0, response1.getTokenCost(), 0.001);
        assertFalse(response1.isHitlStatus());

        // 2. Second request hits the budget breach (> 5.0).
        // Since dailyBudgetEscalated is false, it must trigger a HITL ticket.
        AgentResponse response2 = masterOrchestratorService.processMessage(request);
        assertNotNull(response2);
        assertTrue(response2.isHitlStatus());
        assertNotEquals("BUDGET-EXCEEDED-ACTIVE", response2.getTicketId());
        verify(agentOutPort, times(1)).saveHitlQueue(any(HitlQueue.class));

        // 3. Third request hits the budget breach again.
        // Since dailyBudgetEscalated is now true, it should return BUDGET-EXCEEDED-ACTIVE and NOT
        // call saveHitlQueue again.
        AgentResponse response3 = masterOrchestratorService.processMessage(request);
        assertNotNull(response3);
        assertTrue(response3.isHitlStatus());
        assertEquals("BUDGET-EXCEEDED-ACTIVE", response3.getTicketId());

        // Still times(1) because the second call didn't trigger a new saveHitlQueue
        verify(agentOutPort, times(1)).saveHitlQueue(any(HitlQueue.class));
    }

    @Test
    public void testProcurementBudgetBreachAppliesRuleBasedFallback() {
        // Mock active wholesalers
        ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler w1 =
                ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler.builder()
                        .wholesalerId("w-1")
                        .name("Wholesaler One")
                        .isActive(true)
                        .trustScore(90)
                        .build();
        when(wholesalerPort.findAll()).thenReturn(java.util.Collections.singletonList(w1));

        // Let's stub the guardrail engine to approve
        when(procurementGuardrailsEngine.validate(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(
                        new ProcurementGuardrailsEngine.GuardrailResult(true, "Auto-approving"));

        // 1. Trigger a chat request that costs $6.0 to exceed daily budget
        AgentRequest request = new AgentRequest("Check cost limit", "conv-budget", "cust-1");
        String responseJson =
                "{\"reply\":\"Okay\",\"confidence\":0.9,\"tool\":null,\"tool_argument\":null}";
        when(llmGateway.callLlm(anyString(), anyString()))
                .thenReturn(new LlmResponse(responseJson, 6.0));

        AgentResponse response1 = masterOrchestratorService.processMessage(request);
        assertNotNull(response1);
        assertEquals(6.0, response1.getTokenCost(), 0.001);

        // 2. Perform a B2B procurement negotiation. Since daily budget is exceeded, it must bypass
        // b2BProcurementAgent.negotiateRestock
        // and return the rule-based fallback directly.
        AgentUseCase.NegotiationRequest negotiateRequest = new AgentUseCase.NegotiationRequest();
        negotiateRequest.setItemId("item-123");
        negotiateRequest.setItemName("Super Item");
        negotiateRequest.setBasePrice(100.0);
        negotiateRequest.setQuantity(10);

        AgentUseCase.NegotiationResponse negotiateResponse =
                masterOrchestratorService.negotiateProcurement(negotiateRequest);

        assertNotNull(negotiateResponse);
        assertTrue(negotiateResponse.isApproved());
        assertEquals(
                90.0,
                negotiateResponse.getProposedPrice(),
                0.001); // 10% discount on 100.0 basePrice
        assertEquals(0.50, negotiateResponse.getConfidence(), 0.001);
        assertTrue(negotiateResponse.getRationale().contains("Daily budget limit exceeded"));
        assertEquals("COUNTER_OFFER", negotiateResponse.getWholesalerResponse());
        assertEquals(
                0.0, negotiateResponse.getTokenCost(), 0.001); // 0.0 cost accumulated on fallback

        // Verify that B2B procurement agent was NEVER called
        verify(b2BProcurementAgent, never())
                .negotiateRestock(anyString(), anyString(), anyDouble(), anyString());
        verify(b2BProcurementAgent, never())
                .negotiateRestock(anyString(), anyString(), anyDouble(), anyString(), anyInt());
    }
}
