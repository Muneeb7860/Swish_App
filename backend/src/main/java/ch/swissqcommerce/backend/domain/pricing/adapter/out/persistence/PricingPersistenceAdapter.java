package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.pricing.core.model.Promotion;
import ch.swissqcommerce.backend.domain.pricing.port.out.PricingPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PricingPersistenceAdapter implements PricingPort {
    private final PromotionRepository repository;

    @Override
    public Optional<Promotion> findPromotion(String code) {
        return repository
                .findById(code)
                .map(
                        e ->
                                Promotion.builder()
                                        .code(e.getCode())
                                        .type(e.getType())
                                        .value(e.getValue())
                                        .expiresAt(e.getExpiresAt())
                                        .build());
    }
}
