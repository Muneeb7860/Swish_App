package ch.swissqcommerce.backend.domain.reward.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "customers", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardPoints {

    @Id
    @Column(name = "customer_id", length = 50)
    @Size(max = 50)
    private String customerId;

    @Column(name = "loyalty_points", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer loyaltyPoints = 0;
}
