package ch.swissqcommerce.backend.domain.transaction.adapter.in.web;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.repository.OrderRepository;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = {"http://localhost", "http://127.0.0.1"})
public class OrderController {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private HitlQueueRepository hitlQueueRepository;

    @Data
    public static class OrderRequest {
        @NotBlank(message = "Customer ID is required")
        private String customerId;

        @NotEmpty(message = "Cart items cannot be empty")
        private List<OrderUseCase.CartItem> items;

        @NotBlank(message = "Payment method is required")
        private String paymentMethod;

        private BigDecimal tipAmount = BigDecimal.ZERO;
        private Integer bagsReturned = 0;
    }

    @Data
    public static class RefundRequest {
        @NotBlank(message = "Reason is required")
        private String claimReason;
        private BigDecimal customerLatitude;
        private BigDecimal customerLongitude;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        try {
            Order order = orderUseCase.checkout(
                    request.getCustomerId(),
                    request.getItems(),
                    request.getPaymentMethod(),
                    request.getTipAmount(),
                    request.getBagsReturned(),
                    idempotencyKey
            );
            return ResponseEntity.status(201).body(order);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getCustomerOrders(@RequestParam String customerId) {
        List<Order> orders = orderRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(customerId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> requestRefund(
            @PathVariable Integer id,
            @Valid @RequestBody RefundRequest request) {
        
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Order not found."));
        }
        Order order = orderOpt.get();
        Customer customer = order.getCustomer();

        if (customer.getTrustScore() < 65) {
            return ResponseEntity.status(403).body(Map.of(
                "status", "rejected",
                "message", "REFUND REFUSED: Your account trust rating has fallen below safety thresholds."
            ));
        }

        if (request.getCustomerLatitude() != null && order.getRider() != null) {
            BigDecimal distLat = request.getCustomerLatitude().subtract(order.getRider().getActiveLat()).abs();
            if (distLat.compareTo(new BigDecimal("0.05")) > 0) {
                customer.setTrustScore(Math.max(0, customer.getTrustScore() - 25));
                customerRepository.save(customer);
                return ResponseEntity.status(400).body(Map.of(
                    "status", "rejected",
                    "message", "REFUND BLOCKED: Telemetry Correlation GPS audit failed."
                ));
            }
        }

        String ticketId = "HITL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HitlQueue ticket = HitlQueue.builder()
                .ticketId(ticketId)
                .type("refund_customer")
                .customer(customer)
                .order(order)
                .description("Refund request for order " + id + ". Reason: " + request.getClaimReason())
                .amount(order.getTotalAmount())
                .status("pending")
                .build();
        hitlQueueRepository.save(ticket);

        return ResponseEntity.ok(Map.of(
            "status", "pending_admin_approval",
            "message", "Refund filed. Awaiting manual Admin approval.",
            "ticket_id", ticketId
        ));
    }
}

