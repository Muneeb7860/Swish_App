package ch.swissqcommerce.backend.domain.transaction.core.model;

import ch.swissqcommerce.backend.model.Inventory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    // Back-reference to the parent order. @JsonIgnore breaks the Order <-> OrderItem
    // cycle: without it, serializing an Order graph (e.g. caching getCustomerOrders in
    // Redis) recurses infinitely (StackOverflowError) and every order-list call 500s.
    @JsonIgnore private Order order;

    private Inventory item;

    private Integer quantity;

    private BigDecimal price;
}
