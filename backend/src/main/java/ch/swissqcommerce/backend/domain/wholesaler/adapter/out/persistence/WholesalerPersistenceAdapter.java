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
        return wholesalerRepository.findById(wholesalerId).map(this::toDomain);
    }

    @Override
    public Optional<Wholesaler> findByIsPrimary(Boolean isPrimary) {
        return wholesalerRepository.findByIsPrimary(isPrimary).map(this::toDomain);
    }

    @Override
    public List<Wholesaler> findAll() {
        return wholesalerRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Wholesaler> findFallbackWholesaler(String excludeId, int minTrust) {
        return wholesalerRepository.findFallbackWholesaler(excludeId, minTrust).map(this::toDomain);
    }

    @Override
    public Wholesaler save(Wholesaler wholesaler) {
        return toDomain(wholesalerRepository.save(toEntity(wholesaler)));
    }

    @Override
    public Optional<B2BRestockOrder> findById(Integer restockOrderId) {
        return restockOrderRepository.findById(restockOrderId).map(this::toDomain);
    }

    @Override
    public Optional<B2BRestockOrder> findByIdempotencyKey(String idempotencyKey) {
        return restockOrderRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public List<B2BRestockOrder> findByWholesalerId(String wholesalerId) {
        return restockOrderRepository.findByWholesalerWholesalerIdOrderByCreatedAtDesc(wholesalerId).stream().map(this::toDomain).toList();
    }

    @Override
    public java.util.Map<String, Object> getInvoiceSummaryAggregation(String wholesalerId) {
        return restockOrderRepository.getInvoiceSummaryAggregation(wholesalerId);
    }

    @Override
    public B2BRestockOrder save(B2BRestockOrder restockOrder) {
        return toDomain(restockOrderRepository.save(toEntity(restockOrder)));
    }

    private WholesalerEntity toEntity(Wholesaler w) {
        if (w == null) return null;
        return WholesalerEntity.builder()
            .wholesalerId(w.getWholesalerId())
            .name(w.getName())
            .isPrimary(w.getIsPrimary())
            .trustScore(w.getTrustScore())
            .isActive(w.getIsActive())
            .academyDiscountActive(w.getAcademyDiscountActive())
            .baseInvoiceAmount(w.getBaseInvoiceAmount())
            .fallbackInvoiceAmount(w.getFallbackInvoiceAmount())
            .createdAt(w.getCreatedAt())
            .build();
    }

    private Wholesaler toDomain(WholesalerEntity e) {
        if (e == null) return null;
        return Wholesaler.builder()
            .wholesalerId(e.getWholesalerId())
            .name(e.getName())
            .isPrimary(e.getIsPrimary())
            .trustScore(e.getTrustScore())
            .isActive(e.getIsActive())
            .academyDiscountActive(e.getAcademyDiscountActive())
            .baseInvoiceAmount(e.getBaseInvoiceAmount())
            .fallbackInvoiceAmount(e.getFallbackInvoiceAmount())
            .createdAt(e.getCreatedAt())
            .build();
    }

    private B2BRestockOrderEntity toEntity(B2BRestockOrder o) {
        if (o == null) return null;
        return B2BRestockOrderEntity.builder()
            .restockOrderId(o.getRestockOrderId())
            .store(o.getStore())
            .wholesaler(toEntity(o.getWholesaler()))
            .invoiceAmount(o.getInvoiceAmount())
            .isFallback(o.getIsFallback() != null ? o.getIsFallback() : false)
            .status(o.getStatus() != null ? o.getStatus() : "pending")
            .idempotencyKey(o.getIdempotencyKey())
            .createdAt(o.getCreatedAt())
            .build();
    }

    private B2BRestockOrder toDomain(B2BRestockOrderEntity e) {
        if (e == null) return null;
        return B2BRestockOrder.builder()
            .restockOrderId(e.getRestockOrderId())
            .store(e.getStore())
            .wholesaler(toDomain(e.getWholesaler()))
            .invoiceAmount(e.getInvoiceAmount())
            .isFallback(e.getIsFallback())
            .status(e.getStatus())
            .idempotencyKey(e.getIdempotencyKey())
            .createdAt(e.getCreatedAt())
            .build();
    }
}
