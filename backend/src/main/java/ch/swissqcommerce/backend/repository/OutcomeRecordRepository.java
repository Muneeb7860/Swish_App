package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.OutcomeRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutcomeRecordRepository extends JpaRepository<OutcomeRecord, UUID> {
}
