package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SecurityTrustLedgerRepository extends JpaRepository<SecurityTrustLedger, Integer> {
    List<SecurityTrustLedger> findByActorTypeAndActorIdOrderByTimestampDesc(String actorType, String actorId);
}
