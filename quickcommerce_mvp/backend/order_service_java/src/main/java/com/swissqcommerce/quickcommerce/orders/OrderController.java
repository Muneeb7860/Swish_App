package com.swissqcommerce.quickcommerce.orders;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final List<Order> orders = new ArrayList<>();

    @GetMapping
    public List<Order> listOrders() {
        return orders;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Optional<Order> order = orders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst();
        return order.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order newOrder) {
        newOrder.setId(UUID.randomUUID().toString());
        orders.add(newOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(newOrder);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Order> updateOrder(@PathVariable String orderId, @RequestBody Order updated) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getId().equals(orderId)) {
                updated.setId(orderId);
                orders.set(i, updated);
                return ResponseEntity.ok(updated);
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
