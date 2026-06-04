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
import java.util.Optional;
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

    @Autowired
    private com.platform.core.checkout.adapters.PaymentRepository paymentRepository;

    record TestPaymentIntentResponse(String paymentId, String clientSecret, String status) {}

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

        // When: call the correct intents endpoint
        ResponseEntity<TestPaymentIntentResponse> response = restTemplate.postForEntity(
                "/api/v1/checkout/intents", request, TestPaymentIntentResponse.class);

        // Then: HTTP Response is successful and matches Stripe-style intent format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TestPaymentIntentResponse intentResponse = response.getBody();
        assertThat(intentResponse).isNotNull();
        assertThat(intentResponse.paymentId()).isNotNull();
        assertThat(intentResponse.clientSecret()).startsWith("pi_mock_" + intentResponse.paymentId());
        assertThat(intentResponse.status()).isEqualTo("requires_payment_method");

        // Then: DB Payment entry exists and has INITIATED status
        Long paymentId = Long.parseLong(intentResponse.paymentId());
        Optional<Payment> dbPayment = paymentRepository.findById(paymentId);
        assertThat(dbPayment).isPresent();
        assertThat(dbPayment.get().getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(dbPayment.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(dbPayment.get().getCustomerId()).isEqualTo("CUST-1001");
        assertThat(dbPayment.get().getOrderId()).isEqualTo("ORD-5555");

        // Then: Outbox table contains the event
        List<OutboxEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        OutboxEntity event = outboxEvents.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Payment");
        assertThat(event.getAggregateId()).isEqualTo(intentResponse.paymentId());
        assertThat(event.getType()).isEqualTo("PaymentInitiated");
        assertThat(event.getPayload()).contains(intentResponse.paymentId());
    }
}
