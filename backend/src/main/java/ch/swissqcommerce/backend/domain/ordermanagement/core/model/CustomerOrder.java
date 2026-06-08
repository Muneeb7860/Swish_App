package ch.swissqcommerce.backend.domain.ordermanagement.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrder {
    private String orderId;
    private String customerId;
    private String status; // CREATED, INVENTORY_CONFIRMED, PAYMENT_SUCCESS, DISPATCHED, DELIVERED
    private String sagaState; // PENDING, COMPLETED, COMPENSATING, ABORTED
    private OffsetDateTime createdAt;
}
