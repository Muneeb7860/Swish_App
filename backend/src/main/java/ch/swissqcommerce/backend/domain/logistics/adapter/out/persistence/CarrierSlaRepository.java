package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link CarrierSla}. */
@Repository
public interface CarrierSlaRepository extends JpaRepository<CarrierSla, String> {

    /** Returns all active carrier SLA rules, ordered by carrier name. */
    List<CarrierSla> findByActiveTrueOrderByCarrierAsc();
}
