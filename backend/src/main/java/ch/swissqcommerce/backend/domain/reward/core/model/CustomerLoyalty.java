package ch.swissqcommerce.backend.domain.reward.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_loyalty", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerLoyalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loyalty_id")
    private Integer loyaltyId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "points_changed", nullable = false)
    private Integer pointsChanged;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
