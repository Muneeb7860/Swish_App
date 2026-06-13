package ch.swissqcommerce.backend.domain.pricing.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingEngine {
    private String engineId;
    private List<Promotion> activePromos;
    // Calculation Result is returned instantly
}
