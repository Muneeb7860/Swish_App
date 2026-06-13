package ch.swissqcommerce.backend.domain.transaction.adapter.in.web;

import ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto.*;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("transactionOrderController")
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired private OrderUseCase orderUseCase;

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequestDTO request,
            org.springframework.security.core.Authentication authentication) {
        try {
            // Resolve the customer from the JWT subject when the body omits it, so
            // the SPA need not echo its own id. Object-level authz (IDOR guard):
            // a caller may only check out for themselves unless they are an admin.
            String authId = authentication != null ? authentication.getName() : null;
            String customerId =
                    (request.getCustomerId() != null && !request.getCustomerId().isBlank())
                            ? request.getCustomerId()
                            : authId;
            if (customerId == null || customerId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
            }
            boolean isAdmin =
                    authentication != null
                            && authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!customerId.equals(authId) && !isAdmin) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "Cannot place orders for another customer"));
            }
            Order order =
                    orderUseCase.checkout(
                            customerId,
                            request.getItems(),
                            request.getPaymentMethod(),
                            request.getTipAmount(),
                            request.getBagsReturned(),
                            idempotencyKey);
            return ResponseEntity.status(201).body(mapToDTO(order));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getCustomerOrders(
            @RequestParam(required = false) String customerId,
            org.springframework.security.core.Authentication authentication) {
        // If no explicit customerId is supplied (typical from the Cypress / SPA
        // client), fall back to the authenticated user's own ID encoded in the JWT
        // subject claim.  Return 401 if there is no authenticated principal at all.
        String authId = authentication != null ? authentication.getName() : null;
        if (authId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        String effectiveCustomerId =
                (customerId != null && !customerId.isBlank()) ? customerId : authId;
        // Object-level authz (IDOR guard): only admins may read another customer's orders.
        if (!effectiveCustomerId.equals(authId)
                && authentication.getAuthorities().stream()
                        .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Cannot access another customer's orders"));
        }
        List<Order> orders = orderUseCase.getCustomerOrders(effectiveCustomerId);
        List<OrderResponseDTO> responseDTOs =
                orders.stream().map(this::mapToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(responseDTOs);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> requestRefund(
            @PathVariable Integer id, @Valid @RequestBody RefundRequestDTO request) {
        try {
            Map<String, Object> result =
                    orderUseCase.requestRefund(
                            id,
                            request.getClaimReason(),
                            request.getCustomerLatitude(),
                            request.getCustomerLongitude());

            if ("rejected".equals(result.get("status"))) {
                return ResponseEntity.status(403).body(result);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    private OrderResponseDTO mapToDTO(Order order) {
        return OrderResponseDTO.builder()
                .orderId(order.getOrderId())
                .customerId(
                        order.getCustomer() != null ? order.getCustomer().getCustomerId() : null)
                .storeId(order.getStore() != null ? order.getStore().getStoreId() : null)
                .riderId(order.getRider() != null ? order.getRider().getRiderId() : null)
                .totalAmount(order.getTotalAmount())
                .weatherSurcharge(order.getWeatherSurcharge())
                .tipAmount(order.getTipAmount())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .slaCountdownSec(order.getSlaCountdownSec())
                .bagsReturned(order.getBagsReturned())
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
