package ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler;

import ch.swissqcommerce.backend.model.AgentBaseline;
import ch.swissqcommerce.backend.model.AgentBaselineId;
import ch.swissqcommerce.backend.repository.AgentBaselineRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BaselineJob {

    private static final Logger log = LoggerFactory.getLogger(BaselineJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final AgentBaselineRepository baselineRepo;

    public BaselineJob(JdbcTemplate jdbcTemplate, AgentBaselineRepository baselineRepo) {
        this.jdbcTemplate = jdbcTemplate;
        this.baselineRepo = baselineRepo;
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runBaselineComputationScheduled() {
        log.info("BaselineJob: Triggered scheduled baseline computation.");
        computeBaselines();
    }

    @Transactional
    public void computeBaselines() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        computeBaselinesForDate(yesterday);
    }

    @Transactional
    public void computeBaselinesForDate(LocalDate targetDate) {
        LocalDate startDate = targetDate.minusDays(6); // 7 days inclusive: [targetDate - 6, targetDate]
        
        // Compute time bounds in Java to avoid PostgreSQL-specific date casts (::date, interval)
        Timestamp startTimestamp = Timestamp.from(startDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp endTimestamp = Timestamp.from(targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        log.info("BaselineJob: Computing baselines for date {} using orders from {} to {}", 
                targetDate, startDate, targetDate);

        // Fetch aggregates via JdbcTemplate (read-only, works on both H2 and PostgreSQL)
        List<AgentBaseline> calculated = jdbcTemplate.query("""
            SELECT 
                oi.item_id as sku,
                COALESCE(SUM(oi.price * oi.quantity), 0) as revenue_7d,
                COUNT(DISTINCT o.order_id) as order_count_7d,
                MAX(o.created_at) as last_order_created_at
            FROM oltp.order_items oi
            JOIN oltp.orders o ON oi.order_id = o.order_id
            WHERE o.status = 'delivered'
              AND o.created_at >= ?
              AND o.created_at < ?
            GROUP BY oi.item_id
        """, (rs, rowNum) -> {
            String sku = rs.getString("sku");
            BigDecimal revenue7d = rs.getBigDecimal("revenue_7d");
            int orderCount7d = rs.getInt("order_count_7d");
            Timestamp lastOrderTs = rs.getTimestamp("last_order_created_at");
            
            OffsetDateTime lastOrderCreatedAt = null;
            if (lastOrderTs != null) {
                lastOrderCreatedAt = lastOrderTs.toInstant().atOffset(ZoneOffset.UTC);
            }

            return AgentBaseline.builder()
                    .sku(sku)
                    .date(targetDate)
                    .revenue7d(revenue7d)
                    .marginPct(new BigDecimal("0.20"))
                    .orderCount7d(orderCount7d)
                    .lastOrderCreatedAt(lastOrderCreatedAt)
                    .build();
        }, startTimestamp, endTimestamp);

        log.info("BaselineJob: Found {} SKUs to insert/update for date {}", calculated.size(), targetDate);

        // Upsert via JPA save() — database-agnostic, works on both H2 and PostgreSQL
        for (AgentBaseline baseline : calculated) {
            Optional<AgentBaseline> existing = baselineRepo.findById(
                    new AgentBaselineId(baseline.getSku(), baseline.getDate()));

            if (existing.isPresent()) {
                AgentBaseline record = existing.get();
                record.setRevenue7d(baseline.getRevenue7d());
                record.setMarginPct(baseline.getMarginPct());
                record.setOrderCount7d(baseline.getOrderCount7d());
                record.setLastOrderCreatedAt(baseline.getLastOrderCreatedAt());
                baselineRepo.save(record);
            } else {
                baselineRepo.save(baseline);
            }
        }

        log.info("BaselineJob: Baselines updated successfully for date {}.", targetDate);
    }
}
