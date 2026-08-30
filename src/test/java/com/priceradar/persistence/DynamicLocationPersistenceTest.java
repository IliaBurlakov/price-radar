package com.priceradar.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.region.application.PendingCitySelection;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;
import com.priceradar.region.infrastructure.persistence.JdbcGeoLocationCatalog;
import com.priceradar.region.infrastructure.persistence.JdbcPendingCitySelectionStore;
import com.priceradar.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DynamicLocationPersistenceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void resolvedLocationIsIdempotentAndPendingSelectionSurvivesRestartSafely() throws Exception {
        String schema = "dynamic_location_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = dataSource(schema);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcGeoLocationCatalog catalog = new JdbcGeoLocationCatalog(jdbc);
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        GeoCandidate candidate = candidate("Бердск", "Новосибирская область", "531531");
        WildberriesLocationContext context = new WildberriesLocationContext(-366519L, 30, now);

        Callable<ResolvedLocation> save = () -> catalog.saveResolved(candidate, context, now);
        List<ResolvedLocation> saved;
        try (var executor = Executors.newFixedThreadPool(2)) {
            saved = executor.invokeAll(List.of(save, save)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
        }

        assertThat(saved).extracting(value -> value.getLocation().getId())
                .containsOnly(saved.getFirst().getLocation().getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM geo_locations WHERE normalized_name = 'бердск'", Long.class
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM wildberries_location_contexts WHERE location_id = ?", Long.class,
                saved.getFirst().getLocation().getId()
        )).isEqualTo(1L);

        GeoCandidate firstKirov = candidate("Киров", "Кировская область", "1001");
        GeoCandidate secondKirov = candidate("Киров", "Калужская область", "1002");
        catalog.saveCompletedSearch("киров", List.of(firstKirov, secondKirov), now);
        JdbcGeoLocationCatalog catalogAfterRestart = new JdbcGeoLocationCatalog(jdbc);
        assertThat(catalogAfterRestart.findCompletedSearch("киров").orElseThrow())
                .extracting(location -> location.getRegionName().orElseThrow())
                .containsExactly("Кировская область", "Калужская область");
        assertThat(catalogAfterRestart.findResolvedByIdentityKey(firstKirov.identityKey()))
                .isEmpty();

        UUID owner = insertUser(jdbc, 7001L, now);
        UUID anotherUser = insertUser(jdbc, 7002L, now);
        JdbcPendingCitySelectionStore firstStore = new JdbcPendingCitySelectionStore(jdbc, new ObjectMapper());
        PendingCitySelection pending = new PendingCitySelection(
                UUID.randomUUID(), owner, List.of(candidate), now, now.plusSeconds(900)
        );
        firstStore.replace(pending, now);

        JdbcPendingCitySelectionStore afterRestart = new JdbcPendingCitySelectionStore(jdbc, new ObjectMapper());
        assertThat(afterRestart.findByUserId(owner, now.plusSeconds(1))).get()
                .satisfies(restored -> {
                    assertThat(restored.getId()).isEqualTo(pending.getId());
                    assertThat(restored.getCandidates()).singleElement()
                            .satisfies(value -> assertThat(value.identityKey()).isEqualTo(candidate.identityKey()));
                });
        assertThat(afterRestart.findByUserId(anotherUser, now.plusSeconds(1))).isEmpty();
        assertThat(afterRestart.findByUserId(owner, now.plusSeconds(901))).isEmpty();
    }

    private UUID insertUser(JdbcTemplate jdbc, long telegramId, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_profiles (
                    id, telegram_user_id, telegram_chat_id, location_id,
                    wallet_discount_percent, created_at, updated_at, version
                ) VALUES (?, ?, ?, NULL, 3, ?, ?, 0)
                """, id, telegramId, telegramId, Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private GeoCandidate candidate(String name, String region, String providerId) {
        return new GeoCandidate(
                name, region, null, "Россия", new BigDecimal("54.7582"),
                new BigDecimal("83.1072"), "relation", providerId,
                "place", "town", 0.6, 16
        );
    }

    private DriverManagerDataSource dataSource(String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        dataSource.setSchema(schema);
        return dataSource;
    }
}
