package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, String> {}
