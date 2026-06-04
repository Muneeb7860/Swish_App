package com.platform.notification.consumer;

import com.platform.notification.handler.B2bNotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);
    private final B2bNotificationWebSocketHandler webSocketHandler;

    public NotificationKafkaConsumer(B2bNotificationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Bean
    public Consumer<String> broadcastNotification() {
        return payload -> {
            log.info("Received Kafka evaluation payload: {}", payload);
            
            // Extract customerId. In a real app we would use Jackson ObjectMapper on the Avro GenericRecord
            // For MVP, since the payload might be serialized as JSON String by the binder:
            String targetUserId = "anonymous";
            if (payload.contains("\"customerId\"")) {
                try {
                    String[] parts = payload.split("\"customerId\"\\s*:\\s*\"");
                    if (parts.length > 1) {
                        targetUserId = parts[1].split("\"")[0];
                    }
                } catch (Exception e) {
                    log.error("Failed to parse customerId from payload", e);
                }
            }
            
            // Publish to Redis Backplane instead of local memory broadcast!
            webSocketHandler.publishToUser(targetUserId, payload).subscribe();
        };
    }
}
