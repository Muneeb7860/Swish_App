package ch.swissqcommerce.backend.domain.payment.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponseDTO {
    private Integer paymentId;
    private Integer orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private String idempotencyKey;
    private String externalReference;
    private OffsetDateTime createdAt;
    private OffsetDateTime capturedAt;
    private OffsetDateTime refundedAt;
}
