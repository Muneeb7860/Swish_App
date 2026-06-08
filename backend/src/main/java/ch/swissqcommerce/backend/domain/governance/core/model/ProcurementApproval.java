package ch.swissqcommerce.backend.domain.governance.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementApproval {
    private Integer id;
    private Integer restockOrderId;
    private String wholesalerId;
    private BigDecimal amount;
    private String status; // PENDING, APPROVED, REJECTED
    private String overrideBy;
    private String overrideReason;
}
