package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;

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
public class OrderResponseDTO {
    private Integer orderId;
    private String customerId;
    private String storeId;
    private String riderId;
    private BigDecimal totalAmount;
    private BigDecimal weatherSurcharge;
    private BigDecimal tipAmount;
    private String paymentMethod;
    private String status;
    private Integer slaCountdownSec;
    private Integer bagsReturned;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
