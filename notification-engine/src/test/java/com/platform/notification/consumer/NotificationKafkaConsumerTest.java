package com.platform.notification.consumer;

import com.platform.notification.handler.B2bNotificationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NotificationKafkaConsumerTest {

    @Mock
    private B2bNotificationWebSocketHandler webSocketHandler;

    @InjectMocks
    private NotificationKafkaConsumer consumerConfig;

    @Test
    public void testBroadcastNotification_Success() {
        Consumer<String> consumer = consumerConfig.broadcastNotification();
        String payload = "{\"customerId\": \"user123\", \"status\": \"APPROVED\"}";

        when(webSocketHandler.publishToUser(eq("user123"), anyString())).thenReturn(Mono.empty());

        consumer.accept(payload);

        verify(webSocketHandler).publishToUser(eq("user123"), anyString());
    }

    @Test
    public void testBroadcastNotification_MissingCustomerId() {
        Consumer<String> consumer = consumerConfig.broadcastNotification();
        String payload = "{\"status\": \"APPROVED\"}";

        assertThrows(RuntimeException.class, () -> consumer.accept(payload));
    }

    @Test
    public void testBroadcastNotification_AnonymousUser() {
        Consumer<String> consumer = consumerConfig.broadcastNotification();
        String payload = "{\"customerId\": \"anonymous\", \"status\": \"APPROVED\"}";

        assertThrows(IllegalArgumentException.class, () -> consumer.accept(payload));
    }

    @Test
    public void testBroadcastNotification_InvalidJson() {
        Consumer<String> consumer = consumerConfig.broadcastNotification();
        String payload = "{ invalid json }";

        assertThrows(RuntimeException.class, () -> consumer.accept(payload));
    }
}
