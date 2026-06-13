package ch.swissqcommerce.backend.domain.pricing.core.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationResult {
    private BigDecimal subtotal;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal finalTotal;
}
