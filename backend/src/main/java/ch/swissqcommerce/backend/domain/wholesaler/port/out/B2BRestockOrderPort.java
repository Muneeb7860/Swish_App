package ch.swissqcommerce.backend.domain.wholesaler.port.out;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import java.util.List;
import java.util.Optional;

public interface B2BRestockOrderPort {
    Optional<B2BRestockOrder> findById(Integer restockOrderId);

    Optional<B2BRestockOrder> findByIdempotencyKey(String idempotencyKey);

    List<B2BRestockOrder> findByWholesalerId(String wholesalerId);

    B2BRestockOrder save(B2BRestockOrder restockOrder);
}
