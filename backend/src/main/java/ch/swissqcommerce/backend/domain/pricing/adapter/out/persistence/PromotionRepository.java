package ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<PromotionEntity, String> {
}
