package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import ch.swissqcommerce.backend.domain.agent.adapter.out.gemini.GeminiFreeAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.mock.MockLlmAdapter;
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

@ExtendWith(MockitoExtension.class)
public class PythonGovernanceAdapterTest {

    @Mock
    private GeminiFreeAdapter geminiFreeAdapter;

    @Mock
    private MockLlmAdapter mockLlmAdapter;

    @Mock
    private RestTemplate restTemplate;

    private PythonGovernanceAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new PythonGovernanceAdapter(geminiFreeAdapter, mockLlmAdapter);
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
    public void testCallLlm_NotConfigured_FallbackToMock() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "");
        when(mockLlmAdapter.callLlm("test prompt")).thenReturn(
                LlmResponse.builder().content("mock content").tokenCost(0.1).build()
        );

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertEquals("mock content", response.getContent());
        assertEquals(0.1, response.getTokenCost());
        verify(mockLlmAdapter, times(1)).callLlm("test prompt");
    }

    @Test
    public void testCallLlm_NotConfigured_FallbackToGemini() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "");
        when(geminiFreeAdapter.isConfigured()).thenReturn(true);
        when(geminiFreeAdapter.callLlm("test prompt")).thenReturn(
                LlmResponse.builder().content("gemini content").tokenCost(0.2).build()
        );

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertEquals("gemini content", response.getContent());
        assertEquals(0.2, response.getTokenCost());
        verify(geminiFreeAdapter, times(1)).callLlm("test prompt");
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
    public void testCallLlm_Blocked() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");
        
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "blocked");
        mockResponse.put("message", "rate limit reached");

        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertTrue(response.getContent().contains("Governance Blocked/Failed: rate limit reached"));
        assertEquals(0.0, response.getTokenCost());
    }

    @Test
    public void testCallLlm_Offline_Fallback() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");
        
        when(restTemplate.postForObject(eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        when(mockLlmAdapter.callLlm("test prompt")).thenReturn(
                LlmResponse.builder().content("offline fallback content").tokenCost(0.0).build()
        );

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertEquals("offline fallback content", response.getContent());
        verify(mockLlmAdapter, times(1)).callLlm("test prompt");
    }
}
