package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RewardPointsRepository extends JpaRepository<RewardPointsEntity, String> {}
