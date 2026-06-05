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
            // Simulate generating / retrieving doorstep photo scan proof SHA-256 hash
            String rawProofData = "doorstep-photo-raw-pixels-order-" + event.getOrderId();
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawProofData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String podHash = hexString.toString();

            String signature = governanceUseCase.signDeliverySummary(event.getOrderId(), podHash);
            log.info("ComplianceListener: Successfully generated digital signature for order {} with PoD hash {}: {}", 
                    event.getOrderId(), podHash, signature);
        } catch (Exception e) {
            log.error("ComplianceListener: Failed to generate signed telemetry summary for order {}. Error: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
