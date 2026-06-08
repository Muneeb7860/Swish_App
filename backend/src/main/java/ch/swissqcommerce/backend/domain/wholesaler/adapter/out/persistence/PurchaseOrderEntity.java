package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders", schema = "wholesaler")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class PurchaseOrderEntity {
    @Id
    @Column(name = "po_id")
    private String poId;

    @Column(name = "store_id", nullable = false)
    private String storeId;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(nullable = false)
    private String status; // DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, REJECTED

    @Column(name = "inbound_date")
    private OffsetDateTime inboundDate;

    @Column(name = "grn_verification_file_url")
    private String grnVerificationFileUrl;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderItemEntity> items = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}