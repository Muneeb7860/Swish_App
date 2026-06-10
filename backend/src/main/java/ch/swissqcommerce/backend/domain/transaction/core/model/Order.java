package ch.swissqcommerce.backend.domain.transaction.core.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;


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

    private BigDecimal weatherSurcharge = BigDecimal.ZERO;

    private BigDecimal tipAmount = BigDecimal.ZERO;

    private String paymentMethod;

    private String status = "pending";

    private Integer slaCountdownSec = 540;

    private Integer bagsReturned = 0;

    private String idempotencyKey;

    private OffsetDateTime promisedBy;

    private Boolean containsPerishables = false;

    private Boolean minCartValueMet = true;

    private Boolean storeFaultWaiverApplied = false;

    private BigDecimal perishableMaintenanceFee = BigDecimal.ZERO;

    private OffsetDateTime priceLockedAt;

    private OffsetDateTime createdAt;

    private List<OrderItem> orderItems;

    private String deliveryPin;

    private String proofOfDeliveryPhotoUrl;

    private String rejectionReason;

    private String rejectionPhotoUrl;
}