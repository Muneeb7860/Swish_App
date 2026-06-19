package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    private RiderEntity rider;

    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal totalAmount;

    @Column(name = "weather_surcharge", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal weatherSurcharge = BigDecimal.ZERO;

    @Column(name = "tip_amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String paymentMethod;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "sla_countdown_sec", nullable = false)
    @Builder.Default
    private Integer slaCountdownSec = 540;

    @Column(name = "bags_returned", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer bagsReturned = 0;

    @Column(name = "idempotency_key", length = 100, unique = true)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "promised_by")
    private OffsetDateTime promisedBy;

    @Column(name = "contains_perishables", nullable = false)
    @Builder.Default
    private Boolean containsPerishables = false;

    @Column(name = "min_cart_value_met", nullable = false)
    @Builder.Default
    private Boolean minCartValueMet = true;

    @Column(name = "store_fault_waiver_applied", nullable = false)
    @Builder.Default
    private Boolean storeFaultWaiverApplied = false;

    @Column(name = "perishable_maintenance_fee", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal perishableMaintenanceFee = BigDecimal.ZERO;

    @Column(name = "price_locked_at")
    private OffsetDateTime priceLockedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> orderItems;

    @Column(name = "delivery_pin", length = 4)
    @Size(max = 4)
    private String deliveryPin;

    @Column(name = "proof_of_delivery_photo_url")
    private String proofOfDeliveryPhotoUrl;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "rejection_photo_url")
    private String rejectionPhotoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private DarkStore warehouse;

    @Column(name = "estimated_shipping_cost", precision = 10, scale = 2)
    private BigDecimal estimatedShippingCost;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
