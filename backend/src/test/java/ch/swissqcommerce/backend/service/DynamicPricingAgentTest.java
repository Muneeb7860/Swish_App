package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.core.service.DynamicPricingAgent;
import ch.swissqcommerce.backend.domain.agent.core.service.PricingGuardrailsEngine;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DynamicPricingAgentTest {

    @Mock
    private PythonGovernanceAdapter pythonGovernanceAdapter;

    @Mock
    private GeminiFreeAdapter geminiFreeAdapter;

    @Mock
    private MockLlmAdapter mockLlmAdapter;

    @Spy
    private PricingGuardrailsEngine pricingGuardrailsEngine = new PricingGuardrailsEngine();

    private ExecutorService executorService;
    private DynamicPricingAgent dynamicPricingAgent;

    @BeforeEach
    public void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        dynamicPricingAgent = new DynamicPricingAgent(
                pythonGovernanceAdapter,
                geminiFreeAdapter,
                mockLlmAdapter,
                pricingGuardrailsEngine,
                executorService
        );
        // Inject a default SLA timeout value of 1000ms via reflection to match Spring @Value mapping in tests
        ReflectionTestUtils.setField(dynamicPricingAgent, "slaTimeoutMs", 1000L);
    }

    @AfterEach
    public void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    public void testRecommendPricing_NormalFlow() {
        String jsonResponse = "{\n" +
                "  \"surgeMultiplier\": 1.8,\n" +
                "  \"discountPercent\": 15.0,\n" +
                "  \"confidence\": 0.92,\n" +
                "  \"rationale\": \"Optimal adjustments for rain\"\n" +
                "}";
        
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString())).thenReturn(
                LlmResponse.builder().content(jsonResponse).tokenCost(0.001).build()
        );

        DynamicPricingAgent.PricingAnalysis analysis = dynamicPricingAgent.recommendPricing(
                true, 1.2, 5.0, 3, 0.2
        );

        assertNotNull(analysis);
        assertEquals(1.8, analysis.surgeMultiplier);
        assertEquals(15.0, analysis.discountPercent);
        assertEquals(0.92, analysis.confidence);
        assertTrue(analysis.rationale.contains("Optimal adjustments for rain"));
        assertFalse(analysis.fallbackApplied);
    }

    @Test
    public void testRecommendPricing_LowConfidenceFallback() {
        String jsonResponse = "{\n" +
                "  \"surgeMultiplier\": 2.5,\n" +
                "  \"discountPercent\": 40.0,\n" +
                "  \"confidence\": 0.50,\n" +
                "  \"rationale\": \"High volatility pricing\"\n" +
                "}";

        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString())).thenReturn(
                LlmResponse.builder().content(jsonResponse).tokenCost(0.001).build()
        );

        // Under rain conditions, in-house fallback should yield 2.0 surge
        DynamicPricingAgent.PricingAnalysis analysis = dynamicPricingAgent.recommendPricing(
                true, 1.2, 5.0, 3, 0.2
        );

        assertNotNull(analysis);
        assertEquals(2.0, analysis.surgeMultiplier); // standard rain surge fallback
        assertEquals(0.0, analysis.discountPercent); // non-expiry fallback
        assertEquals(1.0, analysis.confidence);
        assertTrue(analysis.fallbackApplied);
        assertTrue(analysis.rationale.contains("Low Confidence"));
    }

    @Test
    public void testRecommendPricing_GuardrailsClamping() {
        // Surge of 4.5 exceeds limit of 3.0; discount of 60.0% exceeds limit of 50.0%
        String jsonResponse = "{\n" +
                "  \"surgeMultiplier\": 4.5,\n" +
                "  \"discountPercent\": 60.0,\n" +
                "  \"confidence\": 0.95,\n" +
                "  \"rationale\": \"Extreme pricing adjustment\"\n" +
                "}";

        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString())).thenReturn(
                LlmResponse.builder().content(jsonResponse).tokenCost(0.001).build()
        );

        DynamicPricingAgent.PricingAnalysis analysis = dynamicPricingAgent.recommendPricing(
                false, 1.2, 5.0, 3, 0.2
        );

        assertNotNull(analysis);
        assertEquals(3.0, analysis.surgeMultiplier); // Clamped to max limit
        assertEquals(50.0, analysis.discountPercent); // Clamped to max limit
        assertEquals(0.95, analysis.confidence);
        assertFalse(analysis.fallbackApplied);
        assertTrue(analysis.rationale.contains("clamped to maximum"));
    }

    @Test
    public void testRecommendPricing_SlaBreachedTimeoutFallback() {
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString())).thenAnswer(invocation -> {
            Thread.sleep(1500); // Exceeds 1000ms SLA timeout
            return LlmResponse.builder().content("{}").build();
        });

        // Executing under rider ratio < 1.0, expecting surge 1.5 in fallback
        DynamicPricingAgent.PricingAnalysis analysis = dynamicPricingAgent.recommendPricing(
                false, 0.8, 5.0, 1, 0.2
        );

        assertNotNull(analysis);
        assertEquals(1.5, analysis.surgeMultiplier); // fallback rule for low rider ratio
        assertEquals(20.0, analysis.discountPercent); // fallback rule for expiry days <= 2
        assertTrue(analysis.fallbackApplied);
        assertTrue(analysis.rationale.contains("SLA Breached"));
    }
}
