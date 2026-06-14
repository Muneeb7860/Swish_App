package ch.swissqcommerce.backend.domain.transaction.core.model;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    private Integer orderId;

    private Customer customer;

    private DarkStore store;

    private Rider rider;

    private BigDecimal totalAmount;

    @Builder.Default private BigDecimal weatherSurcharge = BigDecimal.ZERO;

    @Builder.Default private BigDecimal tipAmount = BigDecimal.ZERO;

    private String paymentMethod;

    @Builder.Default private String status = "pending";

    @Builder.Default private Integer slaCountdownSec = 540;

    @Builder.Default private Integer bagsReturned = 0;

    private String idempotencyKey;

    private OffsetDateTime promisedBy;

    @Builder.Default private Boolean containsPerishables = false;

    @Builder.Default private Boolean minCartValueMet = true;

    @Builder.Default private Boolean storeFaultWaiverApplied = false;

    @Builder.Default private BigDecimal perishableMaintenanceFee = BigDecimal.ZERO;

    private OffsetDateTime priceLockedAt;

    private OffsetDateTime createdAt;

    private List<OrderItem> orderItems;

    private String deliveryPin;

    private String proofOfDeliveryPhotoUrl;

    private String rejectionReason;

    private String rejectionPhotoUrl;
}
