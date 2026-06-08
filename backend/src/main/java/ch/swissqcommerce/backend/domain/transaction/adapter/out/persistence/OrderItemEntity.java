package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;
import ch.swissqcommerce.backend.model.Inventory;


import ch.swissqcommerce.backend.domain.transaction.core.model.OrderItemId;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


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
@Data
public class OrderItemEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private OrderEntity order;

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