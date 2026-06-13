package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "customers", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(name = "customer_id", length = 50)
    @Size(max = 50)
    private String customerId;

    @Column(name = "full_name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Column(name = "email", length = 100, unique = true)
    @Email
    @Size(max = 100)
    private String email;

    @Column(name = "hashed_email", length = 64, unique = true, nullable = false)
    @NotBlank
    @Size(max = 64)
    private String hashedEmail;

    @Column(name = "wallet_balance", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal walletBalance = new BigDecimal("100.00");

    @Column(name = "loyalty_points", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Column(name = "vip_status", nullable = false)
    @Builder.Default
    private Boolean vipStatus = false;

    @Column(name = "trust_score", nullable = false)
    @Min(0)
    @Max(100)
    @Builder.Default
    private Integer trustScore = 100;

    @Column(name = "is_anonymized", nullable = false)
    @Builder.Default
    private Boolean isAnonymized = false;

    @Column(name = "is_on_probation", nullable = false)
    @Builder.Default
    private Boolean isOnProbation = false;

    @Column(name = "consecutive_orders_completed", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer consecutiveOrdersCompleted = 0;

    @Version private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerAddress> addresses;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerPaymentCard> paymentCards;

    public Integer getTrustScore() {
        return this.trustScore;
    }

    public void setTrustScore(Integer trustScore) {
        this.trustScore = trustScore;
    }

    public Integer getConsecutiveOrdersCompleted() {
        return this.consecutiveOrdersCompleted;
    }

    public void setConsecutiveOrdersCompleted(Integer count) {
        this.consecutiveOrdersCompleted = count;
    }

    public Boolean getVipStatus() {
        return this.vipStatus;
    }

    public void setVipStatus(Boolean vipStatus) {
        this.vipStatus = vipStatus;
    }
}
