package ch.swissqcommerce.backend.domain.transaction.core.model;
import java.math.BigDecimal;
import ch.swissqcommerce.backend.model.Inventory;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    private Order order;

    private Inventory item;

    private Integer quantity;

    private BigDecimal price;
}