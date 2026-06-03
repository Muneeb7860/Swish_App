package ch.swissqcommerce.backend.domain.payment.core.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank
    @Size(max = 3)
    @Builder.Default
    private String currency = "CHF";

    @Column(name = "payment_method", length = 40, nullable = false)
    @NotBlank
    @Size(max = 40)
    private String paymentMethod;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "AUTHORIZED";

    @Column(name = "idempotency_key", length = 100, unique = true)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "external_reference", length = 100)
    @Size(max = 100)
    private String externalReference;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;
}
