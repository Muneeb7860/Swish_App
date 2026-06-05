package ch.swissqcommerce.backend.domain.reward.core.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class RiderLeaderboardService {

    private final StringRedisTemplate redisTemplate;
    private static final String LEADERBOARD_KEY = "rewards:rider:leaderboard";

    public RiderLeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateRiderScore(String riderId, double scoreDelta) {
        redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, riderId, scoreDelta);
    }

    public Set<String> getTopRiders(int limit) {
        return redisTemplate.opsForZSet().reverseRange(LEADERBOARD_KEY, 0, (long) limit - 1);
    }

    public Double getRiderScore(String riderId) {
        return redisTemplate.opsForZSet().score(LEADERBOARD_KEY, riderId);
    }
}
