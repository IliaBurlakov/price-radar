package com.priceradar.statistics.infrastructure.persistence;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.application.ObservedPriceStatistics;
import com.priceradar.statistics.application.PriceStatisticsStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcPriceStatisticsStore implements PriceStatisticsStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPriceStatisticsStore(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ObservedPriceStatistics calculate(
            UUID watchTargetId,
            Instant observedFromInclusive,
            Instant observedToInclusive
    ) {
        if (watchTargetId == null || observedFromInclusive == null || observedToInclusive == null) {
            throw new IllegalArgumentException("statistics query bounds must not be null");
        }
        if (observedFromInclusive.isAfter(observedToInclusive)) {
            throw new IllegalArgumentException("statistics query start must not be after end");
        }
        return jdbcTemplate.queryForObject(
                """
                        WITH observations AS MATERIALIZED (
                            SELECT id, observed_at, regular_price_minor
                            FROM price_snapshots
                            WHERE watch_target_id = ?
                              AND observed_at >= ?
                              AND observed_at <= ?
                              AND status = 'REGULAR_PRICE'
                              AND price_source = 'PRODUCT'
                              AND regular_price_minor > 0
                        )
                        SELECT
                            COUNT(*) AS observation_count,
                            MIN(regular_price_minor) AS minimum_price_minor,
                            MAX(regular_price_minor) AS maximum_price_minor,
                            AVG(regular_price_minor) AS average_price_minor,
                            (
                                SELECT regular_price_minor
                                FROM observations
                                ORDER BY observed_at, id
                                LIMIT 1
                            ) AS first_price_minor,
                            (
                                SELECT regular_price_minor
                                FROM observations
                                ORDER BY observed_at DESC, id DESC
                                LIMIT 1
                            ) AS latest_price_minor,
                            (
                                SELECT observed_at
                                FROM observations
                                ORDER BY regular_price_minor, observed_at, id
                                LIMIT 1
                            ) AS minimum_observed_at
                        FROM observations
                        """,
                this::map,
                watchTargetId,
                Timestamp.from(observedFromInclusive),
                Timestamp.from(observedToInclusive)
        );
    }

    private ObservedPriceStatistics map(ResultSet resultSet, int rowNumber) throws SQLException {
        long observationCount = resultSet.getLong("observation_count");
        if (observationCount == 0) {
            return ObservedPriceStatistics.empty();
        }
        long minimumPriceMinor = resultSet.getLong("minimum_price_minor");
        long maximumPriceMinor = resultSet.getLong("maximum_price_minor");
        BigDecimal averagePriceMinor = resultSet.getBigDecimal("average_price_minor");
        long firstPriceMinor = resultSet.getLong("first_price_minor");
        long latestPriceMinor = resultSet.getLong("latest_price_minor");
        Instant minimumObservedAt = resultSet.getTimestamp("minimum_observed_at").toInstant();
        return ObservedPriceStatistics.of(
                observationCount,
                RubleAmount.ofMinorUnits(minimumPriceMinor),
                minimumObservedAt,
                RubleAmount.ofMinorUnits(maximumPriceMinor),
                averagePriceMinor,
                RubleAmount.ofMinorUnits(firstPriceMinor),
                RubleAmount.ofMinorUnits(latestPriceMinor)
        );
    }
}
