package com.swissqcommerce.quickcommerce.orders;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String id;
    private String customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private double totalAmount;
    private OffsetDateTime createdAt;
}
