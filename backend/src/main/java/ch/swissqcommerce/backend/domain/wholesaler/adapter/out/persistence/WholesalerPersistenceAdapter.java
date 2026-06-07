package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderEntity;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerEntity;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WholesalerPersistenceAdapter implements WholesalerPort, B2BRestockOrderPort {

    @Autowired
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private B2BRestockOrderRepository restockOrderRepository;

    @Override
    public Optional<Wholesaler> findById(String wholesalerId) {
        return wholesalerRepository.findById(wholesalerId);
    }

    @Override
    public Optional<Wholesaler> findByIsPrimary(Boolean isPrimary) {
        return wholesalerRepository.findByIsPrimary(isPrimary);
    }

    @Override
    public List<Wholesaler> findAll() {
        return wholesalerRepository.findAll();
    }

    @Override
    public Wholesaler save(Wholesaler wholesaler) {
        return wholesalerRepository.save(wholesaler);
    }

    @Override
    public Optional<B2BRestockOrder> findById(Integer restockOrderId) {
        return restockOrderRepository.findById(restockOrderId);
    }

    @Override
    public Optional<B2BRestockOrder> findByIdempotencyKey(String idempotencyKey) {
        return restockOrderRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<B2BRestockOrder> findByWholesalerId(String wholesalerId) {
        return restockOrderRepository.findByWholesalerWholesalerIdOrderByCreatedAtDesc(wholesalerId);
    }

    @Override
    public B2BRestockOrder save(B2BRestockOrder restockOrder) {
        return restockOrderRepository.save(restockOrder);
    }
}
