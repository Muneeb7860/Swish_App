package ch.swissqcommerce.backend.domain.payment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;
import ch.swissqcommerce.backend.domain.payment.port.out.TransactionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("paymentTransactionPersistenceAdapter")
public class PaymentTransactionPersistenceAdapter implements TransactionRepositoryPort {

    @Override
    public TransactionRecord save(TransactionRecord transaction) {
        return transaction;
    }

    @Override
    public Optional<TransactionRecord> findById(String transactionId) {
        return Optional.empty();
    }
}
