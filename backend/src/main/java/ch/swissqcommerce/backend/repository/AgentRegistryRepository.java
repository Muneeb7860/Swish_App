package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.AgentRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRegistryRepository extends JpaRepository<AgentRegistry, String> {}
