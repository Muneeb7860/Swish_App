package com.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.notification.handler.B2bNotificationWebSocketHandler;
import com.platform.notification.model.NotificationEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);
    private final B2bNotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationKafkaConsumer(B2bNotificationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Bean
    public Consumer<String> broadcastNotification() {
        return payload -> {
            log.info("Received Kafka evaluation payload: {}", payload);
            
            // Fix #3: Use Jackson ObjectMapper instead of String.split()
            String targetUserId;
            try {
                JsonNode root = objectMapper.readTree(payload);
                JsonNode customerIdNode = root.get("customerId");
                if (customerIdNode == null || customerIdNode.isNull()) {
                    log.error("Payload missing 'customerId' field. Routing to DLQ. Payload: {}", payload);
                    throw new IllegalArgumentException("Missing customerId in notification payload");
                }
                targetUserId = customerIdNode.asText();
            } catch (Exception e) {
                log.error("Failed to parse Kafka payload with Jackson. Message will be retried/DLQ'd.", e);
                throw new RuntimeException("Unparseable notification payload", e);
            }

            // Fix #10: Never route to "anonymous" — reject unknown users
            if (targetUserId == null || targetUserId.isBlank() || "anonymous".equals(targetUserId)) {
                log.error("Invalid targetUserId '{}'. Refusing to broadcast. Payload: {}", targetUserId, payload);
                throw new IllegalArgumentException("Invalid or anonymous targetUserId: " + targetUserId);
            }

            // Fix #12: Wrap in NotificationEnvelope before forwarding
            try {
                NotificationEnvelope envelope = new NotificationEnvelope(
                    UUID.randomUUID().toString(),
                    "ORDER_EVALUATED",
                    1,
                    Instant.now().toString(),
                    targetUserId,
                    objectMapper.readTree(payload),
                    "HIGH"
                );
                String envelopeJson = objectMapper.writeValueAsString(envelope);
                webSocketHandler.publishToUser(targetUserId, envelopeJson).subscribe();
            } catch (Exception e) {
                log.error("Failed to serialize NotificationEnvelope", e);
                throw new RuntimeException("Envelope serialization failed", e);
            }
        };
    }
}
