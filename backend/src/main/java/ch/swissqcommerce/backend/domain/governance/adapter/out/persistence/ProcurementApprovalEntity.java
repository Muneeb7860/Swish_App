package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "procurement_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcurementApprovalEntityEntity {
    @Id
    private String approvalId;
    private String restockOrderId;
    private String status;
    private String reason;
}
