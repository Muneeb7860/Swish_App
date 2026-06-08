import os

base_domain = "backend/src/main/java/ch/swissqcommerce/backend/domain"

controllers = {
    # Phase 1: Auth (already partially there, but let's ensure it's standard)
    f"{base_domain}/auth/adapter/in/web/AuthController.java": """package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    public ResponseEntity<UserAccount> register(@RequestBody UserAccount user) {
        return ResponseEntity.ok(authUseCase.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserAccount user) {
        return ResponseEntity.ok("mock-jwt-token");
    }
}
""",
    # Phase 2: Inventory
    f"{base_domain}/inventory/adapter/in/web/InventoryController.java": """package ch.swissqcommerce.backend.domain.inventory.adapter.in.web;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.port.in.InventoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryUseCase inventoryUseCase;

    @PostMapping("/{sku}/reserve")
    public ResponseEntity<Void> reserveStock(@PathVariable String sku, @RequestParam int amount) {
        inventoryUseCase.reserveStock(sku, amount);
        return ResponseEntity.ok().build();
    }
}
""",
    # Phase 9: Order Management
    f"{base_domain}/ordermanagement/adapter/in/web/OrderController.java": """package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.web;

import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
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
""",
    # Phase 10: Product Catalog
    f"{base_domain}/catalog/adapter/in/web/CatalogController.java": """package ch.swissqcommerce.backend.domain.catalog.adapter.in.web;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogUseCase catalogUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ProductListing> getProduct(@PathVariable String id) {
        return catalogUseCase.getListing(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductListing> createProduct(@RequestBody ProductListing listing) {
        return ResponseEntity.ok(catalogUseCase.createListing(listing));
    }
}
""",
    # Phase 11: Customer Profile
    f"{base_domain}/customer/adapter/in/web/CustomerProfileController.java": """package ch.swissqcommerce.backend.domain.customer.adapter.in.web;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerProfileController {
    private final CustomerProfileUseCase profileUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<CustomerProfile> getProfile(@PathVariable String id) {
        return profileUseCase.getProfile(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CustomerProfile> createProfile(@RequestBody CustomerProfile profile) {
        return ResponseEntity.ok(profileUseCase.createProfile(profile));
    }
}
""",
    # Phase 12: Pricing
    f"{base_domain}/pricing/adapter/in/web/PricingController.java": """package ch.swissqcommerce.backend.domain.pricing.adapter.in.web;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import ch.swissqcommerce.backend.domain.pricing.port.in.PricingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final PricingUseCase pricingUseCase;

    @GetMapping("/calculate")
    public ResponseEntity<CalculationResult> calculateCart(@RequestParam BigDecimal total, @RequestParam(required = false) String code) {
        return ResponseEntity.ok(pricingUseCase.calculate(total, code));
    }
}
""",
    # Phase 13: Fleet
    f"{base_domain}/fleet/adapter/in/web/FleetController.java": """package ch.swissqcommerce.backend.domain.fleet.adapter.in.web;

import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.in.FleetUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
public class FleetController {
    private final FleetUseCase fleetUseCase;

    @PostMapping("/shifts")
    public ResponseEntity<RiderShift> scheduleShift(@RequestBody RiderShift shift) {
        return ResponseEntity.ok(fleetUseCase.scheduleShift(shift));
    }
}
""",
    # Phase 14: Geospatial
    f"{base_domain}/geospatial/adapter/in/web/GeospatialController.java": """package ch.swissqcommerce.backend.domain.geospatial.adapter.in.web;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.in.GeospatialUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/geospatial")
@RequiredArgsConstructor
public class GeospatialController {
    private final GeospatialUseCase geoUseCase;

    @GetMapping("/zones/{id}")
    public ResponseEntity<DeliveryZone> getZone(@PathVariable String id) {
        return geoUseCase.getZone(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/zones")
    public ResponseEntity<DeliveryZone> createZone(@RequestBody DeliveryZone zone) {
        return ResponseEntity.ok(geoUseCase.createZone(zone));
    }
}
""",
    # Phase 15: Support
    f"{base_domain}/support/adapter/in/web/SupportController.java": """package ch.swissqcommerce.backend.domain.support.adapter.in.web;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.in.SupportUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportUseCase supportUseCase;

    @GetMapping("/tickets/{id}")
    public ResponseEntity<SupportTicket> getTicket(@PathVariable String id) {
        return supportUseCase.getTicket(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(@RequestBody SupportTicket ticket) {
        return ResponseEntity.ok(supportUseCase.createTicket(ticket));
    }
}
"""
}

for path, content in controllers.items():
    # Ensure directory exists
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

print("All REST Controllers generated successfully.")
