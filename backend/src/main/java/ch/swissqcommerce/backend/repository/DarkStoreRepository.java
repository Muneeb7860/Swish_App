package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.DarkStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DarkStoreRepository extends JpaRepository<DarkStore, String> {}
