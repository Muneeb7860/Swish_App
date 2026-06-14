package com.platform.core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayConfigurationTest {

    @Mock
    private StreamBridge streamBridge;

    @Mock
    private AesEncryptionConverter aesEncryptionConverter;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private OutboxRelayConfiguration config;

    @Test
    public void testJdbcOutboxPoller() {
        MessageSource<Object> poller = config.jdbcOutboxPoller(dataSource);
        assertNotNull(poller);
        assertTrue(poller instanceof JdbcPollingChannelAdapter);
    }

    @Test
    public void testOutboxMessageHandler() {
        MessageHandler handler = config.outboxMessageHandler();
        assertNotNull(handler);

        List<Map<String, Object>> payloads = List.of(
                Map.of("aggregateType", "Payment", "payload", "encrypted-pay-payload"),
                Map.of("aggregateType", "WholesaleOrder", "payload", "encrypted-whole-payload"),
                Map.of("aggregateType", "Other", "payload", "encrypted-other-payload")
        );
        
        org.mockito.Mockito.when(aesEncryptionConverter.convertToEntityAttribute("encrypted-pay-payload")).thenReturn("pay-payload");
        org.mockito.Mockito.when(aesEncryptionConverter.convertToEntityAttribute("encrypted-whole-payload")).thenReturn("whole-payload");
        org.mockito.Mockito.when(aesEncryptionConverter.convertToEntityAttribute("encrypted-other-payload")).thenReturn("other-payload");

        Message<List<Map<String, Object>>> message = new GenericMessage<>(payloads);
        
        handler.handleMessage(message);

        verify(streamBridge).send(eq("checkoutProcessor-out-0"), eq("pay-payload"));
        verify(streamBridge).send(eq("b2b-wholesale-out-0"), eq("whole-payload"));
        verify(streamBridge).send(eq("enterprise.order.events"), eq("other-payload"));
    }
}
