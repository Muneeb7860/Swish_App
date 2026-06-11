package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.adapter.out.governance.GovernanceBridgeAdapter;
import ch.swissqcommerce.backend.domain.agent.core.model.GovernedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GovernanceBridgeAdapterTest {

    @Test
    void unconfiguredUrl_fallsBackLocal() {
        GovernedResponse r = new GovernanceBridgeAdapter("", mock(RestTemplate.class)).route("hi", "c1");
        assertFalse(r.isRoutedToGovernance());
        assertEquals("local-fallback", r.getAgentId());
        assertEquals("local_fallback", r.getStatus());
    }

    @Test
    void configured_mapsGovernedResponse() {
        RestTemplate rt = mock(RestTemplate.class);
        Map<String, Object> body = Map.of(
                "status", "success",
                "response", "hello there",
                "agent_id", "gemma_reasoner",
                "routing_decision", Map.of("intent", "reasoning", "local_only", true),
                "warnings", List.of("profanity_filter"));
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(body);

        GovernedResponse r = new GovernanceBridgeAdapter("http://governance:8000", rt).route("hi", "c1");

        assertTrue(r.isRoutedToGovernance());
        assertEquals("gemma_reasoner", r.getAgentId());
        assertEquals("hello there", r.getReply());
        assertEquals("reasoning", r.getIntent());
        assertTrue(r.isLocalOnly());
        assertEquals(List.of("profanity_filter"), r.getWarnings());
    }

    @Test
    void blockedResponse_usesMessageAsReply() {
        RestTemplate rt = mock(RestTemplate.class);
        Map<String, Object> body = Map.of(
                "status", "blocked",
                "message", "Request blocked: PII detected",
                "warnings", List.of());
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(body);

        GovernedResponse r = new GovernanceBridgeAdapter("http://governance:8000", rt).route("my ssn is 123-45", "c1");

        assertTrue(r.isRoutedToGovernance());
        assertEquals("blocked", r.getStatus());
        assertEquals("Request blocked: PII detected", r.getReply());
    }

    @Test
    void serviceError_failsSafeLocal() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenThrow(new RuntimeException("connection refused"));

        GovernedResponse r = new GovernanceBridgeAdapter("http://governance:8000", rt).route("hi", "c1");

        assertFalse(r.isRoutedToGovernance());
        assertEquals("local-fallback", r.getAgentId());
    }
}
