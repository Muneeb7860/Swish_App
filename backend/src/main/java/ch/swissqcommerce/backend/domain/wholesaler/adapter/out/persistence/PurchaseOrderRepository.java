package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
}
