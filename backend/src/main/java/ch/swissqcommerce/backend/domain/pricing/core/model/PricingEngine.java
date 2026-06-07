package ch.swissqcommerce.backend.domain.pricing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingEngine {
    private String engineId;
    private List<Promotion> activePromos;
    // Calculation Result is returned instantly
}
