package ch.swissqcommerce.backend.domain.payment.core.service;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;
import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;
import ch.swissqcommerce.backend.domain.payment.core.model.TransactionStatus;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentProcessingUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentGatewayPort;
import ch.swissqcommerce.backend.domain.payment.port.out.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentProcessingUseCase {

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Override
    @Transactional
    public TransactionRecord processCharge(String orderId, Money amount) {
        TransactionRecord transaction = TransactionRecord.builder()
                .transactionId(UUID.randomUUID().toString())
                .orderId(orderId)
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .createdAt(Instant.now())
                .build();
                
        transactionRepositoryPort.save(transaction);
        
        try {
            String gatewayRef = paymentGatewayPort.authorizeAndCapture(orderId, amount);
            transaction.markSettled(gatewayRef);
        } catch (Exception e) {
            transaction.markFailed(e.getMessage());
        }
        
        return transactionRepositoryPort.save(transaction);
    }

    @Override
    @Transactional
    public TransactionRecord processRefund(String transactionId) {
        TransactionRecord transaction = transactionRepositoryPort.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
                
        boolean success = paymentGatewayPort.refund(transaction.getGatewayReference());
        
        if (success) {
            transaction.markRefunded();
        } else {
            throw new IllegalStateException("Gateway refund failed");
        }
        
        return transactionRepositoryPort.save(transaction);
    }
}
