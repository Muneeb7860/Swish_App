package ch.swissqcommerce.backend.domain.pricing.port.in;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import java.math.BigDecimal;

public interface PricingUseCase {
    CalculationResult calculate(BigDecimal cartTotal, String discountCode);
}
