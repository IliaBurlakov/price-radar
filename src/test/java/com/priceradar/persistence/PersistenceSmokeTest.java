package com.priceradar.persistence;

import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PersistenceSmokeTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private ProviderCooldownStore cooldownStore;

    @Test
    void startsContextWithFlywayAndPersistsProviderCooldown() {
        Instant updatedAt = Instant.parse("2026-07-12T10:00:00Z");
        Instant cooldownUntil = Instant.parse("2026-07-12T10:15:00Z");

        cooldownStore.saveCooldownUntil(
                Marketplace.WILDBERRIES,
                cooldownUntil,
                updatedAt
        );

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(cooldownStore.findCooldownUntil(Marketplace.WILDBERRIES))
                .contains(cooldownUntil);
    }
}
