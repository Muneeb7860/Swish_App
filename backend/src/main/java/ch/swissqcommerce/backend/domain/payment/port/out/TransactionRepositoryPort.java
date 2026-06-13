package ch.swissqcommerce.backend.domain.payment.port.out;

import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;
import java.util.Optional;

public interface TransactionRepositoryPort {
    TransactionRecord save(TransactionRecord transaction);

    Optional<TransactionRecord> findById(String transactionId);
}
