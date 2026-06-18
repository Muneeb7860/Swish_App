package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.ExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, Long> {
}
