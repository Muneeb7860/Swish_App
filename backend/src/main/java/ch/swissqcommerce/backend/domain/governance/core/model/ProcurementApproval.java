package ch.swissqcommerce.backend.domain.governance.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "procurement_approvals", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcurementApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "restock_order_id")
    private Integer restockOrderId;

    @Column(name = "wholesaler_id", length = 50)
    private String wholesalerId;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "override_by", length = 100)
    private String overrideBy;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
