package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.OutcomeRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutcomeRecordRepository extends JpaRepository<OutcomeRecord, UUID> {

    @Query("SELECT COUNT(o) FROM OutcomeRecord o WHERE o.suggestion.domain = :domain")
    long countByDomain(@Param("domain") String domain);

    @Query(
            "SELECT COUNT(o) FROM OutcomeRecord o WHERE o.suggestion.domain = :domain AND o.success"
                    + " = true")
    long countSuccessfulByDomain(@Param("domain") String domain);

    @Query("SELECT o.metrics FROM OutcomeRecord o")
    List<String> findAllMetrics();

    default double sumPreventedLossUsd(ObjectMapper objectMapper) {
        return findAllMetrics().stream()
                .mapToDouble(
                        m -> {
                            try {
                                JsonNode node = objectMapper.readTree(m);
                                if (node.has("prevented_chargeback_usd")) {
                                    return node.path("prevented_chargeback_usd").asDouble(0.0);
                                }
                                return 0.0;
                            } catch (Exception e) {
                                return 0.0;
                            }
                        })
                .sum();
    }

    default double sumRevenueDeltaUsd(ObjectMapper objectMapper) {
        return findAllMetrics().stream()
                .mapToDouble(
                        m -> {
                            try {
                                JsonNode node = objectMapper.readTree(m);
                                if (node.has("revenue_delta")) {
                                    return node.path("revenue_delta").asDouble(0.0);
                                }
                                return 0.0;
                            } catch (Exception e) {
                                return 0.0;
                            }
                        })
                .sum();
    }

    default double sumShippingSavingsUsd(ObjectMapper objectMapper) {
        return findAllMetrics().stream()
                .mapToDouble(
                        m -> {
                            try {
                                JsonNode node = objectMapper.readTree(m);
                                if (node.has("shipping_savings_usd")) {
                                    return node.path("shipping_savings_usd").asDouble(0.0);
                                }
                                return 0.0;
                            } catch (Exception e) {
                                return 0.0;
                            }
                        })
                .sum();
    }
}
