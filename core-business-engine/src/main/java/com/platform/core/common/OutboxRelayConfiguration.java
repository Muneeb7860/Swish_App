package com.platform.core.common;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Configuration
public class OutboxRelayConfiguration {

    private final StreamBridge streamBridge;

    public OutboxRelayConfiguration(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Bean
    @InboundChannelAdapter(value = "outboxChannel", poller = @Poller(fixedDelay = "1000"))
    public MessageSource<Object> jdbcOutboxPoller(DataSource dataSource) {
        JdbcPollingChannelAdapter adapter = new JdbcPollingChannelAdapter(dataSource,
                "SELECT id, aggregate_type, aggregate_id, type, payload FROM transactional_outbox WHERE processed_at IS NULL LIMIT 100");
        adapter.setUpdateSql("UPDATE transactional_outbox SET processed_at = CURRENT_TIMESTAMP WHERE id IN (:id)");
        adapter.setRowMapper((rs, rowNum) -> Map.of(
                "id", rs.getString("id"),
                "aggregateType", rs.getString("aggregate_type"),
                "aggregateId", rs.getString("aggregate_id"),
                "type", rs.getString("type"),
                "payload", rs.getString("payload")
        ));
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "outboxChannel")
    @Transactional
    public MessageHandler outboxMessageHandler() {
        return message -> {
            List<Map<String, Object>> payloads = (List<Map<String, Object>>) message.getPayload();
            for (Map<String, Object> outboxEvent : payloads) {
                // Determine the correct Kafka topic / Stream binding based on aggregateType
                String aggregateType = (String) outboxEvent.get("aggregateType");
                String destination = getDestinationForAggregate(aggregateType);
                
                // Publish the event to Spring Cloud Stream
                streamBridge.send(destination, outboxEvent.get("payload"));
            }
        };
    }

    private String getDestinationForAggregate(String aggregateType) {
        if ("Payment".equalsIgnoreCase(aggregateType)) {
            return "checkoutProcessor-out-0";
        } else if ("WholesaleOrder".equalsIgnoreCase(aggregateType)) {
            return "b2b-wholesale-out-0";
        }
        return "enterprise.order.events"; // Default
    }
}
