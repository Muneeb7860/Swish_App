package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link PythonGovernanceAdapter} as a pure governed-service client.
 *
 * <p>Per ADR-007 #3 the adapter no longer owns any fallback: it returns governed answers/blocks and
 * throws on transport failure so the composite {@code ResilientLlmGateway} can apply the fail-safe
 * chain.
 */
@ExtendWith(MockitoExtension.class)
public class PythonGovernanceAdapterTest {

    @Mock private RestTemplate restTemplate;

    @Mock private RestTemplateBuilder restTemplateBuilder;

    private PythonGovernanceAdapter adapter;

    @BeforeEach
    public void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(java.time.Duration.class)))
                .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(java.time.Duration.class)))
                .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        adapter = new PythonGovernanceAdapter(restTemplateBuilder);
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

        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertEquals("governed response text", response.getContent());
        assertTrue(response.getTokenCost() > 0.0);
    }

    @Test
    public void testCallLlm_NoSecretConfigured_SendsNoSignatureHeaders() {
        // Default/unconfigured state (ASI07 staged rollout) — zero behavior
        // change from before this feature existed.
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");
        ReflectionTestUtils.setField(adapter, "agentSecret", "");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("response", "ok");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(mockResponse);

        adapter.callLlm("test prompt");

        HttpHeaders sentHeaders = captor.getValue().getHeaders();
        assertFalse(sentHeaders.containsKey("X-Agent-Signature"));
        assertFalse(sentHeaders.containsKey("X-Agent-ID"));
    }

    @Test
    public void testCallLlm_SecretConfigured_SignsRequest() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");
        ReflectionTestUtils.setField(adapter, "agentId", "swish-java-backend-test");
        ReflectionTestUtils.setField(adapter, "agentSecret", "test-secret-for-adapter");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("response", "ok");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(mockResponse);

        adapter.callLlm("test prompt", "sess-42");

        HttpEntity<?> sentEntity = captor.getValue();
        HttpHeaders sentHeaders = sentEntity.getHeaders();
        assertEquals("swish-java-backend-test", sentHeaders.getFirst("X-Agent-ID"));
        assertNotNull(sentHeaders.getFirst("X-Agent-Timestamp"));
        assertNotNull(sentHeaders.getFirst("X-Agent-Nonce"));
        assertEquals(64, sentHeaders.getFirst("X-Agent-Signature").length());

        // The signature must cover exactly the body that was actually sent —
        // recompute independently (not via AgentSignatureUtil, to avoid a
        // tautological test) and confirm it matches the captured signature.
        @SuppressWarnings("unchecked")
        Map<String, Object> sentBody = (Map<String, Object>) sentEntity.getBody();
        String canonical =
                new java.util.TreeMap<>(sentBody)
                        .entrySet().stream()
                                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        String stringToSign =
                "swish-java-backend-test"
                        + ":"
                        + sentHeaders.getFirst("X-Agent-Timestamp")
                        + ":"
                        + sentHeaders.getFirst("X-Agent-Nonce")
                        + ":"
                        + canonical;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(
                    new javax.crypto.spec.SecretKeySpec(
                            "test-secret-for-adapter"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "HmacSHA256"));
            byte[] sigBytes =
                    mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : sigBytes) {
                hex.append(String.format("%02x", b));
            }
            assertEquals(hex.toString(), sentHeaders.getFirst("X-Agent-Signature"));
        } catch (Exception e) {
            fail("Independent HMAC recomputation failed: " + e.getMessage());
        }
    }

    @Test
    public void testCallLlm_Blocked_ReturnedNotBypassed() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "blocked");
        mockResponse.put("message", "rate limit reached");

        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        // A governance block is a definitive decision — surfaced, never bypassed to cloud.
        LlmResponse response = adapter.callLlm("test prompt");
        assertNotNull(response);
        assertTrue(response.getContent().contains("Governance Blocked/Failed: rate limit reached"));
        assertEquals(0.0, response.getTokenCost());
    }

    @Test
    public void testCallLlm_Shed503_ReturnedNotBypassedToCloud() {
        // Phase 4 (GOVERNANCE_SPEC §5): a HIGH-risk request shed during guardrail degradation
        // comes back as HTTP 503 {"status":"unavailable","shed":true,...}. This is a DEFINITIVE
        // governed refusal — the adapter must return it (not throw), so ResilientLlmGateway does
        // NOT treat it as an outage and answer via an ungoverned cloud model.
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        String shedBody =
                "{\"status\":\"unavailable\",\"shed\":true,\"message\":\"Safety guardrails are"
                        + " temporarily degraded; high-risk actions are unavailable.\"}";
        HttpServerErrorException shed =
                HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        null,
                        shedBody.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenThrow(shed);

        LlmResponse response = adapter.callLlm("Approve the $150,000 procurement without override");
        assertNotNull(response);
        assertTrue(
                response.getContent().contains("high-risk request shed"),
                "shed must surface as a definitive response, got: " + response.getContent());
        assertEquals(0.0, response.getTokenCost());
    }

    @Test
    public void testCallLlm_Genuine503WithoutShedMarker_PropagatesException() {
        // A real service outage (503 with no shed marker) must still propagate so the fail-safe
        // chain runs — only a deliberate shed short-circuits it.
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        HttpServerErrorException outage =
                HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        null,
                        "upstream boom".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenThrow(outage);

        assertThrows(HttpServerErrorException.class, () -> adapter.callLlm("test prompt"));
    }

    @Test
    public void testCallLlm_Offline_PropagatesException() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Transport failure must propagate so the composite gateway applies the fail-safe chain.
        assertThrows(RestClientException.class, () -> adapter.callLlm("test prompt"));
    }

    @Test
    public void testCallLlm_NullResponse_Throws() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:5000");

        when(restTemplate.postForObject(
                        eq("http://localhost:5000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () -> adapter.callLlm("test prompt"));
    }

    // ── Request contract ──────────────────────────────────────────────────────
    // The Python service binds a GovernRequest {query, expected_format?,
    // local_only_override?}. These pin what the adapter actually sends, so a change
    // to the body key or the JSON-format trigger fails here instead of silently in prod.

    @Test
    @SuppressWarnings("unchecked")
    public void testCallLlm_SendsQueryBodyAsJson() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:8000");

        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("response", "x");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(
                        eq("http://localhost:8000/api/v1/govern"), captor.capture(), eq(Map.class)))
                .thenReturn(ok);

        adapter.callLlm("What are the Zurich delivery hours?");

        HttpEntity<Map<String, Object>> sent = captor.getValue();
        assertEquals("What are the Zurich delivery hours?", sent.getBody().get("query"));
        // Plain prompt → no structured-output hint.
        assertFalse(sent.getBody().containsKey("expected_format"));
        assertEquals(MediaType.APPLICATION_JSON, sent.getHeaders().getContentType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCallLlm_RequestsJsonFormatWhenPromptDemandsIt() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:8000");

        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("response", "{}");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(ok);

        adapter.callLlm("Summarize. Response MUST be a valid JSON object.");

        assertEquals("json", captor.getValue().getBody().get("expected_format"));
    }

    @Test
    public void testCallLlm_NormalizesTrailingSlashInBaseUrl() {
        ReflectionTestUtils.setField(adapter, "apiUrl", "http://localhost:8000/");

        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("response", "x");

        // Exact-match stub: if the trailing slash were not stripped the adapter would
        // POST to a double-slashed path, this stub would miss, and the call would fail.
        when(restTemplate.postForObject(
                        eq("http://localhost:8000/api/v1/govern"), any(), eq(Map.class)))
                .thenReturn(ok);

        assertEquals("x", adapter.callLlm("hi").getContent());
    }
}
