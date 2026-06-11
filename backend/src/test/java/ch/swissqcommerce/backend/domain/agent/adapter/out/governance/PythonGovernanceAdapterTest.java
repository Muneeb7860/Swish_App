package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PythonGovernanceAdapter} as a pure governed-service client.
 *
 * Per ADR-007 #3 the adapter no longer owns any fallback: it returns governed
 * answers/blocks and throws on transport failure so the composite
 * {@code ResilientLlmGateway} can apply the fail-safe chain.
 */
@ExtendWith(MockitoExtension.class)
public class PythonGovernanceAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private PythonGovernanceAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new PythonGovernanceAdapter();
        ReflectionTestUtils.setField(adapter, "restTemplate", restTemplate);
    }

    @Test
    public void testIsConfigured_EmptyApiUrl() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "");
        assertFalse(adapter.isConfigured());

        ReflectionTestUtils.setField(adapter, "apiUrl", "   ");
        assertFalse(adapter.isConfigured());

        ReflectionTestUtils.setField(adapter, "apiUrl", null);
        assertFalse(adapter.isConfigured());
    }

    @Test
    public void testIsConfigured_ValidApiUrl() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");
        assertTrue(adapter.isConfigured());
    }

    @Test
    public void testCallLlm_NotConfigured_Throws() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "");
        // Selection is the composite's responsibility; calling an unconfigured client is a bug.
        assertThrows(IllegalStateException.class, () -> adapter.callLlm("test prompt"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    public void testCallLlm_Success() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("response", "governed response text");

        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertEquals("governed response text", response.getContent());
        assertTrue(response.getTokenCost() > 0.0);
    }

    @Test
    public void testCallLlm_Blocked_ReturnedNotBypassed() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "blocked");
        mockResponse.put("message", "rate limit reached");

        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        // A governance block is a definitive decision — surfaced, never bypassed to cloud.
        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertTrue(response.getContent().contains("Governance Blocked/Failed: rate limit reached"));
        assertEquals(0.0, response.getTokenCost());
    }

    @Test
    public void testCallLlm_Offline_PropagatesException() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Transport failure must propagate so the composite gateway applies the fail-safe chain.
        assertThrows(RestClientException.class, () -> adapter.callLlm("test prompt"));
    }

    @Test
    public void testCallLlm_NullResponse_Throws() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () -> adapter.callLlm("test prompt"));
    }
}
