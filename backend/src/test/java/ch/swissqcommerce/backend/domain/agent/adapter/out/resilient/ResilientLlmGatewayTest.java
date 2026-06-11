package ch.swissqcommerce.backend.domain.agent.adapter.out.resilient;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.PythonGovernanceAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.pii.PiiPreScanner;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Security-critical tests for the fail-safe fallback chain (ADR-007 #2).
 *
 * The invariant under test: a PII-bearing prompt is NEVER sent to the cloud
 * (Gemini) when governance is unavailable — it fails SAFE, not OPEN.
 */
@ExtendWith(MockitoExtension.class)
public class ResilientLlmGatewayTest {

    @Mock private PythonGovernanceAdapter pythonGovernanceAdapter;
    @Mock private GeminiFreeAdapter geminiFreeAdapter;
    @Mock private MockLlmAdapter mockLlmAdapter;

    // Real scanner — we want the genuine regex behaviour, not a mock.
    private final PiiPreScanner piiPreScanner = new PiiPreScanner();

    private ResilientLlmGateway gateway;

    private static final String PII_PROMPT = "Contact the customer at john.doe@example.com about the refund.";
    private static final String CLEAN_PROMPT = "Summarise the weather impact on delivery demand.";

    @BeforeEach
    public void setUp() {
        gateway = new ResilientLlmGateway(
                pythonGovernanceAdapter, geminiFreeAdapter, mockLlmAdapter, piiPreScanner);
    }

    // ─── Preferred path: governance configured ───────────────────────────────

    @Test
    public void governanceConfigured_usesGovernance_neverTouchesCloud() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(true);
        when(pythonGovernanceAdapter.callLlm(anyString()))
                .thenReturn(LlmResponse.builder().content("governed").tokenCost(0.5).build());

        LlmResponse res = gateway.callLlm(PII_PROMPT);

        assertEquals("governed", res.getContent());
        verify(pythonGovernanceAdapter).callLlm(PII_PROMPT);
        verifyNoInteractions(geminiFreeAdapter);
        verifyNoInteractions(mockLlmAdapter);
    }

    // ─── THE FIX: governance down + PII must NOT reach cloud ──────────────────

    @Test
    public void governanceDown_withPii_blocksCloud_returnsDegraded() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(true);
        when(pythonGovernanceAdapter.callLlm(anyString()))
                .thenThrow(new RestClientException("governance offline"));
        // Cloud is available — but PII must keep it off-limits.
        lenient().when(geminiFreeAdapter.isConfigured()).thenReturn(true);

        LlmResponse res = gateway.callLlm(PII_PROMPT);

        assertEquals(ResilientLlmGateway.GOVERNANCE_DEGRADED, res.getContent());
        assertEquals(0.0, res.getTokenCost());
        verify(geminiFreeAdapter, never()).callLlm(anyString());
    }

    @Test
    public void governanceUnconfigured_withPii_blocksCloud_returnsDegraded() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(false);
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);

        LlmResponse res = gateway.callLlm(PII_PROMPT);

        assertEquals(ResilientLlmGateway.GOVERNANCE_DEGRADED, res.getContent());
        verify(geminiFreeAdapter, never()).callLlm(anyString());
        verify(pythonGovernanceAdapter, never()).callLlm(anyString());
    }

    // ─── Gated cloud: PII-free prompts may use cloud as a fallback ────────────

    @Test
    public void governanceDown_cleanPrompt_usesGatedCloud() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(true);
        when(pythonGovernanceAdapter.callLlm(anyString()))
                .thenThrow(new RestClientException("governance offline"));
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString()))
                .thenReturn(LlmResponse.builder().content("cloud answer").tokenCost(0.2).build());

        LlmResponse res = gateway.callLlm(CLEAN_PROMPT);

        assertEquals("cloud answer", res.getContent());
        verify(geminiFreeAdapter).callLlm(CLEAN_PROMPT);
    }

    @Test
    public void governanceUnconfigured_cleanPrompt_usesGatedCloud() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(false);
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm(anyString()))
                .thenReturn(LlmResponse.builder().content("cloud answer").tokenCost(0.2).build());

        LlmResponse res = gateway.callLlm(CLEAN_PROMPT);

        assertEquals("cloud answer", res.getContent());
        verify(geminiFreeAdapter).callLlm(CLEAN_PROMPT);
    }

    // ─── Default dev/CI: nothing configured → local mock, never cloud ─────────

    @Test
    public void nothingConfigured_usesMock_neverCloud() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(false);
        when(geminiFreeAdapter.isConfigured()).thenReturn(false);
        when(mockLlmAdapter.callLlm(anyString()))
                .thenReturn(LlmResponse.builder().content("mock").tokenCost(0.0).build());

        LlmResponse res = gateway.callLlm(CLEAN_PROMPT);

        assertEquals("mock", res.getContent());
        verify(geminiFreeAdapter, never()).callLlm(anyString());
        verify(mockLlmAdapter).callLlm(CLEAN_PROMPT);
    }

    @Test
    public void governanceDown_noCloud_withPii_usesMock() {
        when(pythonGovernanceAdapter.isConfigured()).thenReturn(true);
        when(pythonGovernanceAdapter.callLlm(anyString()))
                .thenThrow(new RestClientException("governance offline"));
        when(geminiFreeAdapter.isConfigured()).thenReturn(false);
        when(mockLlmAdapter.callLlm(anyString()))
                .thenReturn(LlmResponse.builder().content("mock").tokenCost(0.0).build());

        LlmResponse res = gateway.callLlm(PII_PROMPT);

        assertEquals("mock", res.getContent());
        verify(mockLlmAdapter).callLlm(PII_PROMPT);
    }
}
