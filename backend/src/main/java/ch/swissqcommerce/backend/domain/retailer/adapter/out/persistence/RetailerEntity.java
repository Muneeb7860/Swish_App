package ch.swissqcommerce.backend.domain.retailer.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "retailers", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetailerEntity {

    @Id
    @Column(name = "retailer_id", length = 50)
    private String retailerId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "contact_email", length = 255, nullable = false)
    private String contactEmail;

    @Column(name = "store_id", length = 50)
    private String storeId;

    @Column(name = "tier", length = 20, nullable = false)
    private String tier;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "approval_ops", nullable = false)
    private boolean approvalOps;

    @Column(name = "approval_compliance", nullable = false)
    private boolean approvalCompliance;

    @Column(name = "approval_admin", nullable = false)
    private boolean approvalAdmin;

    @Column(name = "api_key_hash", length = 64)
    private String apiKeyHash;

    @Column(name = "billing_account_id", length = 50)
    private String billingAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if ("ACTIVE".equals(status) && activatedAt == null) {
            activatedAt = OffsetDateTime.now();
        }
    }
}
