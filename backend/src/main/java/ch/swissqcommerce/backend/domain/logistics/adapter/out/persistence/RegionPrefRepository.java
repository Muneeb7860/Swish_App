package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionPrefRepository extends JpaRepository<RegionPref, String> {
}
