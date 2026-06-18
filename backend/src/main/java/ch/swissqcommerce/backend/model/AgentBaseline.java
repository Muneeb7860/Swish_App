package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "agent_baseline", schema = "oltp")
@IdClass(AgentBaselineId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentBaseline {

    @Id
    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "revenue_7d", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue7d;

    @Column(name = "margin_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal marginPct;

    @Column(name = "order_count_7d", nullable = false)
    private Integer orderCount7d;

    @Column(name = "last_order_created_at")
    private OffsetDateTime lastOrderCreatedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
