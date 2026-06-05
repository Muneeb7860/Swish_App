package ch.swissqcommerce.backend.domain.governance.adapter.in.event;

import ch.swissqcommerce.backend.domain.event.core.model.OrderFulfilledEvent;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ComplianceListener {

    private static final Logger log = LoggerFactory.getLogger(ComplianceListener.class);
    private final GovernanceUseCase governanceUseCase;

    public ComplianceListener(GovernanceUseCase governanceUseCase) {
        this.governanceUseCase = governanceUseCase;
    }

    @Async
    @EventListener
    public void handleOrderFulfilled(OrderFulfilledEvent event) {
        log.info("ComplianceListener: Received OrderFulfilledEvent for order id={}, generating signed GDP telemetry summary.", 
                event.getOrderId());
        try {
            String signature = governanceUseCase.signDeliverySummary(event.getOrderId());
            log.info("ComplianceListener: Successfully generated digital signature for order {}: {}", 
                    event.getOrderId(), signature);
        } catch (Exception e) {
            log.error("ComplianceListener: Failed to generate signed telemetry summary for order {}. Error: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
