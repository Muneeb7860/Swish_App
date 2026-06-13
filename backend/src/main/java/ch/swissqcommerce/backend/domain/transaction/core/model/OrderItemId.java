package ch.swissqcommerce.backend.domain.transaction.core.model;

import ch.swissqcommerce.backend.model.*;
import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemId implements Serializable {
    private Integer order; // Maps to order field in OrderItem
    private String item; // Maps to item field in OrderItem
}
