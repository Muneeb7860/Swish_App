package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.config.LettaConfig;
import ch.swissqcommerce.backend.domain.agent.core.service.LettaMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class LettaMemoryServiceTest {

    @Mock
    private LettaConfig lettaConfig;

    @Mock
    private RestTemplate restTemplate;

    private LettaMemoryService lettaMemoryService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(lettaConfig.getApiUrl()).thenReturn("http://localhost:8283");
        when(lettaConfig.getApiToken()).thenReturn("dummy-key");
        when(lettaConfig.getModel()).thenReturn("openai/gpt-4o");
        lettaMemoryService = new LettaMemoryService(lettaConfig, restTemplate);
    }

    @Test
    public void testSendMessageWithExistingAgent() {
        Map<String, Object> listBody = new HashMap<>();
        Map<String, String> agentInfo = new HashMap<>();
        agentInfo.put("id", "agent-123");
        agentInfo.put("name", "agent-conv-session-xyz");
        listBody.put("items", Collections.singletonList(agentInfo));

        ResponseEntity<Object> listResponse = new ResponseEntity<>(listBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8283/v1/agents"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(listResponse);

        Map<String, Object> messageBody = new HashMap<>();
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Hello from Letta!");
        messageBody.put("messages", Collections.singletonList(assistantMsg));

        ResponseEntity<Object> messageResponse = new ResponseEntity<>(messageBody, HttpStatus.OK);
        when(restTemplate.postForEntity(
                eq("http://localhost:8283/v1/agents/agent-123/messages"),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(messageResponse);

        String result = lettaMemoryService.sendMessage("session-xyz", "Hi agent!");
        assertEquals("Hello from Letta!", result);

        verify(restTemplate, never()).postForEntity(
                eq("http://localhost:8283/v1/agents"),
                any(HttpEntity.class),
                eq(Object.class)
        );
    }

    @Test
    public void testSendMessageWithNewAgentCreation() {
        Map<String, Object> listBody = new HashMap<>();
        listBody.put("items", Collections.emptyList());

        ResponseEntity<Object> listResponse = new ResponseEntity<>(listBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8283/v1/agents"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(listResponse);

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("id", "agent-456");
        createBody.put("name", "agent-conv-session-new");

        ResponseEntity<Object> createResponse = new ResponseEntity<>(createBody, HttpStatus.OK);
        when(restTemplate.postForEntity(
                eq("http://localhost:8283/v1/agents"),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(createResponse);

        Map<String, Object> messageBody = new HashMap<>();
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Welcome, new user!");
        messageBody.put("messages", Collections.singletonList(assistantMsg));

        ResponseEntity<Object> messageResponse = new ResponseEntity<>(messageBody, HttpStatus.OK);
        when(restTemplate.postForEntity(
                eq("http://localhost:8283/v1/agents/agent-456/messages"),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(messageResponse);

        String result = lettaMemoryService.sendMessage("session-new", "Hi agent!");
        assertEquals("Welcome, new user!", result);

        verify(restTemplate, times(1)).postForEntity(
                eq("http://localhost:8283/v1/agents"),
                any(HttpEntity.class),
                eq(Object.class)
        );
    }

    @Test
    public void testSendMessageResilientFallbackOnConnectionFailure() {
        when(restTemplate.exchange(
                eq("http://localhost:8283/v1/agents"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenThrow(new RestClientException("Connection refused"));

        String result = lettaMemoryService.sendMessage("session-xyz", "Hi agent!");
        assertNull(result);
    }

    @Test
    public void testSendMessageUsesConfiguredTokenAndModel() {
        when(lettaConfig.getApiToken()).thenReturn("my-secret-token");
        when(lettaConfig.getModel()).thenReturn("my-custom-model");

        Map<String, Object> listBody = new HashMap<>();
        listBody.put("items", Collections.emptyList());

        ResponseEntity<Object> listResponse = new ResponseEntity<>(listBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8283/v1/agents"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(listResponse);

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("id", "agent-456");
        createBody.put("name", "agent-conv-session-new");

        ResponseEntity<Object> createResponse = new ResponseEntity<>(createBody, HttpStatus.OK);
        when(restTemplate.postForEntity(
                eq("http://localhost:8283/v1/agents"),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(createResponse);

        Map<String, Object> messageBody = new HashMap<>();
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Welcome!");
        messageBody.put("messages", Collections.singletonList(assistantMsg));

        ResponseEntity<Object> messageResponse = new ResponseEntity<>(messageBody, HttpStatus.OK);
        when(restTemplate.postForEntity(
                eq("http://localhost:8283/v1/agents/agent-456/messages"),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(messageResponse);

        lettaMemoryService.sendMessage("session-new", "Hi agent!");

        // Verify that the GET agents list request used our custom API token
        org.mockito.ArgumentCaptor<HttpEntity> getEntityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                getEntityCaptor.capture(),
                eq(Object.class)
        );
        assertEquals("Bearer my-secret-token", getEntityCaptor.getValue().getHeaders().getFirst("Authorization"));

        // Verify that the POST create agent request used our custom model in its body
        org.mockito.ArgumentCaptor<HttpEntity> createEntityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://localhost:8283/v1/agents"),
                createEntityCaptor.capture(),
                eq(Object.class)
        );
        Map<?, ?> bodyMap = (Map<?, ?>) createEntityCaptor.getValue().getBody();
        assertEquals("my-custom-model", bodyMap.get("model"));
        assertEquals("Bearer my-secret-token", createEntityCaptor.getValue().getHeaders().getFirst("Authorization"));
    }
}
