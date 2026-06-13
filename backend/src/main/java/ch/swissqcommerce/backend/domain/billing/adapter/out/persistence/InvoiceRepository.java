package ch.swissqcommerce.backend.domain.billing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {
    List<InvoiceEntity> findByAccountIdOrderByIssuedAtDesc(String accountId);
}
