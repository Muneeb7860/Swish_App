import os

base_paths = [
    "backend/src/main/java/ch/swissqcommerce/backend/domain/catalog",
    "backend/src/main/java/ch/swissqcommerce/backend/domain/customer",
    "backend/src/main/java/ch/swissqcommerce/backend/domain/pricing"
]

for base_path in base_paths:
    dirs = [
        f"{base_path}/core/model",
        f"{base_path}/core/service",
        f"{base_path}/port/in",
        f"{base_path}/port/out",
        f"{base_path}/adapter/in/event",
        f"{base_path}/adapter/in/web",
        f"{base_path}/adapter/out/persistence",
    ]
    for d in dirs:
        os.makedirs(d, exist_ok=True)

files = {
    # Phase 10: Product Catalog
    f"{base_paths[0]}/core/model/ProductListing.java": """package ch.swissqcommerce.backend.domain.catalog.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListing {
    private String productId;
    private String title;
    private String description;
    private List<Image> gallery;
    private BigDecimal basePrice;
    private String status; // ACTIVE, ARCHIVED

    public void updatePrice(BigDecimal newPrice) { this.basePrice = newPrice; }
    public void archive() { this.status = "ARCHIVED"; }
}
""",
    f"{base_paths[0]}/core/model/Image.java": """package ch.swissqcommerce.backend.domain.catalog.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Image {
    private String url;
    private String altText;
    private boolean isPrimary;
}
""",
    f"{base_paths[0]}/port/in/CatalogUseCase.java": """package ch.swissqcommerce.backend.domain.catalog.port.in;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import java.util.Optional;

public interface CatalogUseCase {
    ProductListing createListing(ProductListing listing);
    Optional<ProductListing> getListing(String productId);
}
""",
    f"{base_paths[0]}/port/out/CatalogPort.java": """package ch.swissqcommerce.backend.domain.catalog.port.out;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import java.util.Optional;

public interface CatalogPort {
    ProductListing save(ProductListing listing);
    Optional<ProductListing> findById(String productId);
}
""",
    f"{base_paths[0]}/core/service/CatalogServiceImpl.java": """package ch.swissqcommerce.backend.domain.catalog.core.service;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogUseCase {
    private final CatalogPort port;

    @Override
    public ProductListing createListing(ProductListing listing) {
        if (listing.getProductId() == null) listing.setProductId(UUID.randomUUID().toString());
        return port.save(listing);
    }

    @Override
    public Optional<ProductListing> getListing(String productId) {
        return port.findById(productId);
    }
}
""",
    f"{base_paths[0]}/adapter/out/persistence/ProductListingEntity.java": """package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "product_listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListingEntity {
    @Id
    private String productId;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private String status;
}
""",
    f"{base_paths[0]}/adapter/out/persistence/CatalogRepository.java": """package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<ProductListingEntity, String> {
}
""",
    f"{base_paths[0]}/adapter/out/persistence/CatalogPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogPersistenceAdapter implements CatalogPort {
    private final CatalogRepository repository;

    @Override
    public ProductListing save(ProductListing listing) {
        ProductListingEntity entity = ProductListingEntity.builder()
                .productId(listing.getProductId())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .basePrice(listing.getBasePrice())
                .status(listing.getStatus())
                .build();
        repository.save(entity);
        return listing;
    }

    @Override
    public Optional<ProductListing> findById(String productId) {
        return repository.findById(productId).map(e -> ProductListing.builder()
                .productId(e.getProductId())
                .title(e.getTitle())
                .description(e.getDescription())
                .basePrice(e.getBasePrice())
                .status(e.getStatus())
                .build());
    }
}
""",

    # Phase 11: Customer Profile
    f"{base_paths[1]}/core/model/CustomerProfile.java": """package ch.swissqcommerce.backend.domain.customer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfile {
    private String profileId;
    private String userId;
    private Preferences prefs;
    private List<DeliveryAddress> addressBook;

    public void addAddress(DeliveryAddress address) {
        if(addressBook == null) addressBook = new ArrayList<>();
        addressBook.add(address);
    }
    public void removeAddress(String addressId) {
        if(addressBook != null) addressBook.removeIf(a -> a.getAddressId().equals(addressId));
    }
    public void updatePreferences(Preferences prefs) {
        this.prefs = prefs;
    }
}
""",
    f"{base_paths[1]}/core/model/DeliveryAddress.java": """package ch.swissqcommerce.backend.domain.customer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {
    private String addressId;
    private String label;
    private String street;
    private String city;
    private String geoHash;
}
""",
    f"{base_paths[1]}/core/model/Preferences.java": """package ch.swissqcommerce.backend.domain.customer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Preferences {
    private boolean marketingOptIn;
    private String defaultCurrency;
}
""",
    f"{base_paths[1]}/port/in/CustomerProfileUseCase.java": """package ch.swissqcommerce.backend.domain.customer.port.in;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import java.util.Optional;

public interface CustomerProfileUseCase {
    CustomerProfile createProfile(CustomerProfile profile);
    Optional<CustomerProfile> getProfile(String profileId);
}
""",
    f"{base_paths[1]}/port/out/CustomerProfilePort.java": """package ch.swissqcommerce.backend.domain.customer.port.out;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import java.util.Optional;

public interface CustomerProfilePort {
    CustomerProfile save(CustomerProfile profile);
    Optional<CustomerProfile> findById(String profileId);
}
""",
    f"{base_paths[1]}/core/service/CustomerProfileServiceImpl.java": """package ch.swissqcommerce.backend.domain.customer.core.service;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerProfileUseCase;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileUseCase {
    private final CustomerProfilePort port;

    @Override
    public CustomerProfile createProfile(CustomerProfile profile) {
        if (profile.getProfileId() == null) profile.setProfileId(UUID.randomUUID().toString());
        return port.save(profile);
    }

    @Override
    public Optional<CustomerProfile> getProfile(String profileId) {
        return port.findById(profileId);
    }
}
""",
    f"{base_paths[1]}/adapter/out/persistence/CustomerProfileEntity.java": """package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileEntity {
    @Id
    private String profileId;
    private String userId;
    private boolean marketingOptIn;
    private String defaultCurrency;
}
""",
    f"{base_paths[1]}/adapter/out/persistence/CustomerProfileRepository.java": """package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfileEntity, String> {
}
""",
    f"{base_paths[1]}/adapter/out/persistence/CustomerProfilePersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.core.model.Preferences;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerProfilePersistenceAdapter implements CustomerProfilePort {
    private final CustomerProfileRepository repository;

    @Override
    public CustomerProfile save(CustomerProfile profile) {
        CustomerProfileEntity entity = CustomerProfileEntity.builder()
                .profileId(profile.getProfileId())
                .userId(profile.getUserId())
                .marketingOptIn(profile.getPrefs() != null && profile.getPrefs().isMarketingOptIn())
                .defaultCurrency(profile.getPrefs() != null ? profile.getPrefs().getDefaultCurrency() : "CHF")
                .build();
        repository.save(entity);
        return profile;
    }

    @Override
    public Optional<CustomerProfile> findById(String profileId) {
        return repository.findById(profileId).map(e -> CustomerProfile.builder()
                .profileId(e.getProfileId())
                .userId(e.getUserId())
                .prefs(Preferences.builder().marketingOptIn(e.isMarketingOptIn()).defaultCurrency(e.getDefaultCurrency()).build())
                .build());
    }
}
""",

    # Phase 12: Pricing
    f"{base_paths[2]}/core/model/PricingEngine.java": """package ch.swissqcommerce.backend.domain.pricing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingEngine {
    private String engineId;
    private List<Promotion> activePromos;
    // Calculation Result is returned instantly
}
""",
    f"{base_paths[2]}/core/model/Promotion.java": """package ch.swissqcommerce.backend.domain.pricing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {
    private String code;
    private String type; // PERCENTAGE, FIXED_AMOUNT
    private BigDecimal value;
    private OffsetDateTime expiresAt;
}
""",
    f"{base_paths[2]}/core/model/CalculationResult.java": """package ch.swissqcommerce.backend.domain.pricing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationResult {
    private BigDecimal subtotal;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal finalTotal;
}
""",
    f"{base_paths[2]}/port/in/PricingUseCase.java": """package ch.swissqcommerce.backend.domain.pricing.port.in;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import java.math.BigDecimal;

public interface PricingUseCase {
    CalculationResult calculate(BigDecimal cartTotal, String discountCode);
}
""",
    f"{base_paths[2]}/port/out/PricingPort.java": """package ch.swissqcommerce.backend.domain.pricing.port.out;

import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import java.util.Optional;

public interface PricingPort {
    Optional<Promotion> findPromotion(String code);
}
""",
    f"{base_paths[2]}/core/service/PricingServiceImpl.java": """package ch.swissqcommerce.backend.domain.pricing.core.service;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import ch.swissqcommerce.backend.domain.pricing.port.in.PricingUseCase;
import ch.swissqcommerce.backend.domain.pricing.port.out.PricingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingUseCase {
    private final PricingPort port;

    @Override
    public CalculationResult calculate(BigDecimal cartTotal, String discountCode) {
        BigDecimal discount = BigDecimal.ZERO;
        
        if (discountCode != null && !discountCode.isEmpty()) {
            port.findPromotion(discountCode).ifPresent(p -> {
                if(p.getExpiresAt().isAfter(OffsetDateTime.now())) {
                    // Apply discount logic
                }
            });
        }

        BigDecimal tax = cartTotal.multiply(new BigDecimal("0.07"));
        BigDecimal finalTotal = cartTotal.subtract(discount).add(tax);

        return CalculationResult.builder()
                .subtotal(cartTotal)
                .totalDiscount(discount)
                .totalTax(tax)
                .finalTotal(finalTotal)
                .build();
    }
}
""",
    f"{base_paths[2]}/adapter/out/persistence/PromotionEntity.java": """package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "promotions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionEntity {
    @Id
    private String code;
    private String type;
    private BigDecimal value;
    private OffsetDateTime expiresAt;
}
""",
    f"{base_paths[2]}/adapter/out/persistence/PromotionRepository.java": """package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<PromotionEntity, String> {
}
""",
    f"{base_paths[2]}/adapter/out/persistence/PricingPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import ch.swissqcommerce.backend.domain.pricing.port.out.PricingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PricingPersistenceAdapter implements PricingPort {
    private final PromotionRepository repository;

    @Override
    public Optional<Promotion> findPromotion(String code) {
        return repository.findById(code).map(e -> Promotion.builder()
                .code(e.getCode())
                .type(e.getType())
                .value(e.getValue())
                .expiresAt(e.getExpiresAt())
                .build());
    }
}
"""
}

for path, content in files.items():
    with open(path, "w") as f:
        f.write(content)

print("Phases 10, 11, and 12 scaffolded successfully!")
