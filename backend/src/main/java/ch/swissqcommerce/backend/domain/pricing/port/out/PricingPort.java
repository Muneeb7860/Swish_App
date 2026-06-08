package ch.swissqcommerce.backend.domain.pricing.port.out;

import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import java.util.Optional;

public interface PricingPort {
    Optional<Promotion> findPromotion(String code);
}
