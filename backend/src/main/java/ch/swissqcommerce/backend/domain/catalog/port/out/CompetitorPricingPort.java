package ch.swissqcommerce.backend.domain.catalog.port.out;

import java.util.Optional;

public interface CompetitorPricingPort {
    Optional<Double> fetchCompetitorPrice(String barcode);
}
