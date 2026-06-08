package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WastageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("domainWastageLogRepository")
public interface WastageLogRepository extends JpaRepository<WastageLogEntity, String> {
}
