package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityTrustLedgerRepository extends JpaRepository<SecurityTrustLedger, Integer> {
    List<SecurityTrustLedger> findByActorTypeAndActorIdOrderByTimestampDesc(
            String actorType, String actorId);
}
