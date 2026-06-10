package ch.swissqcommerce.backend.domain.billing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {
    List<InvoiceEntity> findByAccountIdOrderByIssuedAtDesc(String accountId);
}
