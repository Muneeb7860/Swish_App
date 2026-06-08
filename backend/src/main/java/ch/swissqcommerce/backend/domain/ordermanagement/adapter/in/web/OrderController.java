package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.web;

import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("orderManagementOrderController")
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderManagementUseCase orderUseCase;

    @PostMapping
    public ResponseEntity<Void> placeOrder(@RequestParam String orderId, @RequestParam String customerId) {
        // This triggers the Saga
        orderUseCase.handleOrderCreated(orderId, customerId);
        return ResponseEntity.accepted().build();
    }
}
