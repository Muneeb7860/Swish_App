package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("domainWastageLogRepository")
public interface WastageLogEntityRepository extends JpaRepository<WastageLogEntity, String> {}
