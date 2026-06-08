package ch.swissqcommerce.backend.domain.pricing.adapter.in.web;

import ch.swissqcommerce.backend.domain.pricing.core.model.CalculationResult;
import ch.swissqcommerce.backend.domain.pricing.port.in.PricingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final PricingUseCase pricingUseCase;

    @GetMapping("/calculate")
    public ResponseEntity<CalculationResult> calculateCart(@RequestParam BigDecimal total, @RequestParam(required = false) String code) {
        return ResponseEntity.ok(pricingUseCase.calculate(total, code));
    }
}
