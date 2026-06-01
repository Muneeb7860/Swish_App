package ch.swissqcommerce.backend.domain;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RewardPointsTest {

    @Test
    void shouldCreateNewRewardPoints() {
        RewardPoints rp = RewardPoints.builder()
            .customerId("CUST-999")
            .loyaltyPoints(500)
            .build();

        assertNotNull(rp);
        assertEquals("CUST-999", rp.getCustomerId());
        assertEquals(500, rp.getLoyaltyPoints());
    }

    @Test
    void shouldInitializeWithZeroPoints() {
        RewardPoints rp = new RewardPoints();
        rp.setCustomerId("CUST-000");

        assertNotNull(rp);
        assertEquals(0, rp.getLoyaltyPoints());
    }
}
