package ch.swissqcommerce.backend.domain.payment.core.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private Integer paymentId;

    private Integer orderId;

    private String customerId;

    private BigDecimal amount;

    private String currency = "CHF";

    private String paymentMethod;

    private String status = "AUTHORIZED";

    private String idempotencyKey;

    private String externalReference;

    private OffsetDateTime createdAt;

    private OffsetDateTime capturedAt;

    private OffsetDateTime refundedAt;
}
