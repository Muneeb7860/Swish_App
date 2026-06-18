package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "chargebacks", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chargeback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chargeback_id")
    private Long chargebackId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "filed_at", nullable = false)
    @Builder.Default
    private OffsetDateTime filedAt = OffsetDateTime.now();

    @Column(name = "status", length = 50, nullable = false)
    @Builder.Default
    private String status = "disputed"; // disputed, won, lost
}
