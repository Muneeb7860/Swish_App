package ch.swissqcommerce.backend.domain.payment.core.model;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
public class TransactionRecord {
    private final String transactionId;
    private final String orderId;
    private final Money amount;
    private TransactionStatus status;
    private String gatewayReference;
    private Instant createdAt;
    private Instant processedAt;

    public void markSettled(String gatewayRef) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Transaction is not pending");
        }
        this.status = TransactionStatus.SETTLED;
        this.gatewayReference = gatewayRef;
        this.processedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.processedAt = Instant.now();
    }
    
    public void markRefunded() {
        if (this.status != TransactionStatus.SETTLED) {
            throw new IllegalStateException("Cannot refund an unsettled transaction");
        }
        this.status = TransactionStatus.REFUNDED;
        this.processedAt = Instant.now();
    }
}
