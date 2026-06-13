package ch.swissqcommerce.backend.domain.pricing.adapter.in.web;

import ch.swissqcommerce.backend.domain.pricing.port.in.PricingUseCase;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final PricingUseCase pricingUseCase;

    @GetMapping("/calculate")
    public ResponseEntity<?> calculateCart(
            @RequestParam BigDecimal total, @RequestParam(required = false) String code) {
        // Reject negative cart totals so a crafted request cannot drive the
        // discount engine into nonsensical or negative-charge territory.
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "total must be present and non-negative."));
        }
        return ResponseEntity.ok(pricingUseCase.calculate(total, code));
    }
}
