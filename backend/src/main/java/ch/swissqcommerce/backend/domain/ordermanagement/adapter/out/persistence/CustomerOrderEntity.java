package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_customer_orders", schema = "oltp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderEntity {
    @Id private String orderId;
    private String customerId;
    private String status;
    private String sagaState;
    private OffsetDateTime createdAt;
}
