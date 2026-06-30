package ch.swissqcommerce.backend.domain.logistics.adapter.in.web;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase.RoutingResult;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for RoutingAgent v1.0 operations. Allows administrators/operators to trigger the
 * routing logic for an order manually.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Saves resources by shedding load using a Semaphore concurrency limit.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/routing")
public class RoutingController {

    private static final Logger log = LoggerFactory.getLogger(RoutingController.class);

    private final LogisticsDataPort logisticsDataPort;
    private final WarehouseSelectionUseCase warehouseSelectionUseCase;
    final Semaphore semaphore;

    public RoutingController(
            LogisticsDataPort logisticsDataPort,
            WarehouseSelectionUseCase warehouseSelectionUseCase,
            @Value("${routing.load-shedding.max-concurrent:10}") int maxConcurrent) {
        this.logisticsDataPort = logisticsDataPort;
        this.warehouseSelectionUseCase = warehouseSelectionUseCase;
        this.semaphore = new Semaphore(maxConcurrent);
    }

    /**
     * POST /api/v1/routing/orders/{orderId} Triggers warehouse routing optimization for the given
     * orderId. Requires ROLE_ADMIN.
     *
     * @param orderId the order ID to route
     * @return 200 OK with RoutingResult, 404 if order not found, 409 Conflict if auto-routing
     *     fails, 429 Too Many Requests if the concurrency limit is exceeded (load shedding)
     */
    @PostMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> routeOrder(@PathVariable Integer orderId) {
        if (!semaphore.tryAcquire()) {
            log.warn("Routing load shedded for orderId={} due to high concurrency", orderId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "System is under heavy load. Please try again later."));
        }

        try {
            log.info("REST request to route orderId={}", orderId);

            Optional<RoutingOrderData> orderDataOpt =
                    logisticsDataPort.findRoutingOrderData(orderId);
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
                        .body(
                                Map.of(
                                        "error",
                                        "Automatic routing failed. Order sent to HITL queue."));
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
        } finally {
            semaphore.release();
        }
    }
}
