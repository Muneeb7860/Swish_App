package ch.swissqcommerce.backend.domain.logistics.adapter.in.web;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase.RoutingResult;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for RoutingAgent v1.0 operations. Allows administrators/operators to trigger the
 * routing logic for an order manually.
 */
@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
public class RoutingController {

    private static final Logger log = LoggerFactory.getLogger(RoutingController.class);

    private final LogisticsDataPort logisticsDataPort;
    private final WarehouseSelectionUseCase warehouseSelectionUseCase;

    /**
     * POST /api/v1/routing/orders/{orderId} Triggers warehouse routing optimization for the given
     * orderId. Requires ROLE_ADMIN.
     *
     * @param orderId the order ID to route
     * @return 200 OK with RoutingResult, 404 if order not found, 409 Conflict if auto-routing fails
     */
    @PostMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> routeOrder(@PathVariable Integer orderId) {
        log.info("REST request to route orderId={}", orderId);

        Optional<RoutingOrderData> orderDataOpt = logisticsDataPort.findRoutingOrderData(orderId);
        if (orderDataOpt.isEmpty()) {
            log.warn("Routing failed: Order ID {} not found", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found with ID " + orderId));
        }

        RoutingOrderData orderData = orderDataOpt.get();
        Optional<RoutingResult> resultOpt =
                warehouseSelectionUseCase.findOptimalWarehouse(orderData);

        if (resultOpt.isEmpty()) {
            log.warn("Routing failed (Conflict/HITL needed) for orderId={}", orderId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Automatic routing failed. Order sent to HITL queue."));
        }

        RoutingResult result = resultOpt.get();
        log.info(
                "Routing successful for orderId={}, selected primaryWarehouseId={}, carrier={},"
                        + " packages={}",
                orderId,
                result.primaryWarehouseId(),
                result.carrier(),
                result.packageCount());

        return ResponseEntity.ok(result);
    }
}
