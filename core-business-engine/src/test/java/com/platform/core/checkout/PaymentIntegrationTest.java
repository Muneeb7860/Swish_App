package com.platform.core.checkout;

import com.platform.core.checkout.domain.Payment;
import com.platform.core.checkout.domain.PaymentStatus;
import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("b2b_qcomm")
            .withUsername("admin")
            .withPassword("adminpassword");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", () -> "localhost"); // Assuming Mock or omitted for this isolated test
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void shouldProcessPaymentAndWriteToOutbox() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = """
                {
                    "customerId": "CUST-1001",
                    "orderId": "ORD-5555",
                    "amount": 499.99
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", idempotencyKey);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Payment> response = restTemplate.postForEntity("/api/checkout/payments", request, Payment.class);

        // Then: HTTP Response is successful
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Payment createdPayment = response.getBody();
        assertThat(createdPayment).isNotNull();
        assertThat(createdPayment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(createdPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Then: Outbox table contains the event
        List<OutboxEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        OutboxEntity event = outboxEvents.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Payment");
        assertThat(event.getAggregateId()).isEqualTo(String.valueOf(createdPayment.getId()));
        assertThat(event.getType()).isEqualTo("PaymentInitiated");
        assertThat(event.getPayload()).contains(String.valueOf(createdPayment.getId()));
    }
}
