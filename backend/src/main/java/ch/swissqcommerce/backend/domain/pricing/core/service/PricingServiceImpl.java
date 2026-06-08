package ch.swissqcommerce.backend.domain.pricing.core.service;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import ch.swissqcommerce.backend.domain.pricing.port.in.PricingUseCase;
import ch.swissqcommerce.backend.domain.pricing.port.out.PricingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingUseCase {
    private final PricingPort port;

    @Override
    public CalculationResult calculate(BigDecimal cartTotal, String discountCode) {
        BigDecimal discount = BigDecimal.ZERO;
        
        if (discountCode != null && !discountCode.isEmpty()) {
            port.findPromotion(discountCode).ifPresent(p -> {
                if(p.getExpiresAt().isAfter(OffsetDateTime.now())) {
                    // Apply discount logic
                }
            });
        }

        BigDecimal tax = cartTotal.multiply(new BigDecimal("0.07"));
        BigDecimal finalTotal = cartTotal.subtract(discount).add(tax);

        return CalculationResult.builder()
                .subtotal(cartTotal)
                .totalDiscount(discount)
                .totalTax(tax)
                .finalTotal(finalTotal)
                .build();
    }
}
