package ch.swissqcommerce.backend.domain.transaction.core.model;

import ch.swissqcommerce.backend.model.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "order_items", schema = "oltp")
@IdClass(OrderItemId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Inventory item;

    @Column(name = "quantity", nullable = false)
    @NotNull
    @Min(1)
    private Integer quantity;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal price;
}
