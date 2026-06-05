package ch.swissqcommerce.backend.domain.reward.adapter.in.event;

import ch.swissqcommerce.backend.domain.event.core.model.OrderFulfilledEvent;
import ch.swissqcommerce.backend.domain.reward.core.service.RiderLeaderboardService;
import ch.swissqcommerce.backend.domain.reward.port.in.RewardUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RewardsListener {

    private static final Logger log = LoggerFactory.getLogger(RewardsListener.class);

    private final RewardUseCase rewardUseCase;
    private final RiderLeaderboardService leaderboardService;
    private final OrderPort orderPort;

    public RewardsListener(RewardUseCase rewardUseCase,
                           RiderLeaderboardService leaderboardService,
                           OrderPort orderPort) {
        this.rewardUseCase = rewardUseCase;
        this.leaderboardService = leaderboardService;
        this.orderPort = orderPort;
    }

    @Async
    @EventListener
    public void handleOrderFulfilled(OrderFulfilledEvent event) {
        log.info("RewardsListener: Received OrderFulfilledEvent for order id={}", event.getOrderId());
        try {
            // 1. Credit loyalty points to the customer
            if (event.getCustomerId() != null) {
                rewardUseCase.addPoints(event.getCustomerId(), event.getRewardPoints());
            }

            // 2. Fetch the order to get the associated rider
            try {
                int orderIdInt = Integer.parseInt(event.getOrderId());
                orderPort.findById(orderIdInt).ifPresent(order -> {
                    if (order.getRider() != null && order.getRider().getRiderId() != null) {
                        String riderId = order.getRider().getRiderId();
                        // Grant rider 10 points on the leaderboard for a successful delivery
                        leaderboardService.updateRiderScore(riderId, 10.0);
                        log.info("RewardsListener: Credited rider id={} with 10.0 leaderboard points.", riderId);
                    }
                });
            } catch (NumberFormatException nfe) {
                log.warn("RewardsListener: Invalid orderId format: {}", event.getOrderId());
            }

        } catch (Exception e) {
            log.error("RewardsListener: Failed to process rewards for order id={}. Error: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
