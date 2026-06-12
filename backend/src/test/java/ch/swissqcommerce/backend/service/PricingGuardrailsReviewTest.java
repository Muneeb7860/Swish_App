package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.service.PricingGuardrailsEngine;
import ch.swissqcommerce.backend.domain.agent.core.service.PricingGuardrailsEngine.GuardrailResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8: the pricing engine flags aggressive (but in-range) recommendations for
 * supervisor review — surge &gt; 2.5x or discount &gt; 15% — while still returning a
 * clamped, safe recommendation. Below those thresholds nothing is flagged.
 */
class PricingGuardrailsReviewTest {

    private final PricingGuardrailsEngine engine = new PricingGuardrailsEngine();

    @Test
    void normalValues_noReview() {
        GuardrailResult r = engine.validateAndClamp(2.0, 10.0);
        assertFalse(r.isNeedsReview());
        assertEquals(2.0, r.getValidatedSurgeMultiplier(), 1e-9);
        assertEquals(10.0, r.getValidatedDiscountPercent(), 1e-9);
    }

    @Test
    void surgeAboveReviewThreshold_flagged() {
        GuardrailResult r = engine.validateAndClamp(2.6, 0.0);
        assertTrue(r.isNeedsReview());
        assertTrue(r.getReviewReason().toLowerCase().contains("surge"));
        // still within the hard cap, so not clamped
        assertEquals(2.6, r.getValidatedSurgeMultiplier(), 1e-9);
    }

    @Test
    void discountAboveReviewThreshold_flagged() {
        GuardrailResult r = engine.validateAndClamp(1.5, 20.0);
        assertTrue(r.isNeedsReview());
        assertTrue(r.getReviewReason().toLowerCase().contains("discount"));
    }

    @Test
    void extremeValues_clampedAndFlagged() {
        GuardrailResult r = engine.validateAndClamp(3.5, 60.0);
        assertTrue(r.isNeedsReview(), "values beyond the cap are also beyond the review threshold");
        assertEquals(3.0, r.getValidatedSurgeMultiplier(), 1e-9, "clamped to max surge");
        assertEquals(50.0, r.getValidatedDiscountPercent(), 1e-9, "clamped to max discount");
        assertTrue(r.isAdjusted());
    }

    @Test
    void boundaryValues_notFlagged() {
        // exactly at the thresholds is NOT over them
        GuardrailResult r = engine.validateAndClamp(2.5, 15.0);
        assertFalse(r.isNeedsReview());
    }
}
