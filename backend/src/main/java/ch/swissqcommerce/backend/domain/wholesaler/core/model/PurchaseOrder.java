package ch.swissqcommerce.backend.domain.wholesaler.core.model;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    private String poId;

    private String storeId;

    private String vendorName;

    private String status; // DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, REJECTED

    private OffsetDateTime inboundDate;

    private String grnVerificationFileUrl;

    private List<PurchaseOrderItem> items = new ArrayList<>();

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
    
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
    
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}