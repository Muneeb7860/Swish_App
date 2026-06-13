package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.config.LettaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class LettaMemoryService {

    private final LettaConfig lettaConfig;
    private final RestTemplate lettaRestTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Sends a message to a stateful Letta agent associated with the conversationId. If the agent
     * does not exist, it will be created.
     */
    public String sendMessage(String conversationId, String messageContent) {
        try {
            String agentId = getOrCreateAgent(conversationId);
            if (agentId == null) {
                log.warn(
                        "Could not retrieve or create Letta agent for conversationId: {}",
                        conversationId);
                incrementFallbackCounter();
                return null;
            }

            String url = lettaConfig.getApiUrl() + "/v1/agents/" + agentId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + lettaConfig.getApiToken());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", messageContent);
            requestBody.put("role", "user");
            requestBody.put("input", messageContent);
            requestBody.put("streaming", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Object> response =
                    lettaRestTemplate.postForEntity(url, entity, Object.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object body = response.getBody();
                List<?> messagesList = null;
                if (body instanceof Map) {
                    Map<?, ?> mapBody = (Map<?, ?>) body;
                    if (mapBody.containsKey("messages")) {
                        messagesList = (List<?>) mapBody.get("messages");
                    } else if (mapBody.containsKey("items")) {
                        messagesList = (List<?>) mapBody.get("items");
                    } else if (mapBody.containsKey("results")) {
                        messagesList = (List<?>) mapBody.get("results");
                    }
                } else if (body instanceof List) {
                    messagesList = (List<?>) body;
                }

                if (messagesList != null && !messagesList.isEmpty()) {
                    for (int i = messagesList.size() - 1; i >= 0; i--) {
                        Object msgObj = messagesList.get(i);
                        if (msgObj instanceof Map) {
                            Map<?, ?> msg = (Map<?, ?>) msgObj;
                            String role = (String) msg.get("role");
                            String text = (String) msg.get("content");
                            if ("assistant".equalsIgnoreCase(role)
                                    && text != null
                                    && !text.trim().isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }
            log.warn("Letta server did not return a valid assistant message.");
            incrementFallbackCounter();
        } catch (Exception e) {
            log.error("Error communicating with Letta Agent Server: {}", e.getMessage());
            incrementFallbackCounter();
        }
        return null;
    }

    private void incrementFallbackCounter() {
        if (meterRegistry != null) {
            meterRegistry.counter("letta.fallback.triggers", "service", "letta-memory").increment();
        }
    }

    private String getOrCreateAgent(String conversationId) {
        String agentName = "agent-conv-" + conversationId.replaceAll("[^a-zA-Z0-9_-]", "-");
        try {
            String listUrl = lettaConfig.getApiUrl() + "/v1/agents";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + lettaConfig.getApiToken());
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            ResponseEntity<Object> listResponse =
                    lettaRestTemplate.exchange(
                            listUrl,
                            org.springframework.http.HttpMethod.GET,
                            getEntity,
                            Object.class);

            if (listResponse.getStatusCode().is2xxSuccessful() && listResponse.getBody() != null) {
                Object body = listResponse.getBody();
                List<?> items = null;
                if (body instanceof List) {
                    items = (List<?>) body;
                } else if (body instanceof Map) {
                    Map<?, ?> mapBody = (Map<?, ?>) body;
                    if (mapBody.containsKey("items")) {
                        items = (List<?>) mapBody.get("items");
                    } else if (mapBody.containsKey("results")) {
                        items = (List<?>) mapBody.get("results");
                    } else if (mapBody.containsKey("agents")) {
                        items = (List<?>) mapBody.get("agents");
                    }
                }

                if (items != null) {
                    for (Object itemObj : items) {
                        if (itemObj instanceof Map) {
                            Map<?, ?> item = (Map<?, ?>) itemObj;
                            String name = (String) item.get("name");
                            if (agentName.equalsIgnoreCase(name)) {
                                return (String) item.get("id");
                            }
                        }
                    }
                }
            }

            String createUrl = lettaConfig.getApiUrl() + "/v1/agents";
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> createBody = new HashMap<>();
            createBody.put("name", agentName);
            createBody.put("model", lettaConfig.getModel());

            HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, headers);
            ResponseEntity<Object> createResponse =
                    lettaRestTemplate.postForEntity(createUrl, createEntity, Object.class);

            if (createResponse.getStatusCode().is2xxSuccessful()
                    && createResponse.getBody() != null) {
                Object body = createResponse.getBody();
                if (body instanceof Map) {
                    return (String) ((Map<?, ?>) body).get("id");
                }
            }
        } catch (Exception e) {
            log.error("Failed to check or create Letta agent: {}", e.getMessage());
        }
        return null;
    }
}
