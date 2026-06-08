package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "promotions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionEntity {
    @Id
    private String code;
    private String type;
    @Column(name = "\"value\"")
    private BigDecimal value;
    private OffsetDateTime expiresAt;
}