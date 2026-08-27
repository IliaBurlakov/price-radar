package com.priceradar.telegram.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.telegram.application.MultiProductQuoteItem;
import com.priceradar.telegram.application.MultiProductQuoteSession;
import com.priceradar.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MultiProductQuoteSessionPersistenceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @Test
    void roundTripsOwnedSessionAndResolvedQuotePayload() {
        String schema = "multi_quote_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = dataSource(schema);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-27T08:00:00Z");
        jdbc.update("""
                INSERT INTO user_profiles (
                    id, telegram_user_id, telegram_chat_id, region_code, region_selected,
                    wallet_discount_percent, created_at, updated_at, version
                ) VALUES (?, 7001, 7001, 'MOSCOW', TRUE, 3, ?, ?, 0)
                """, userId, Timestamp.from(now), Timestamp.from(now));
        JdbcMultiProductQuoteSessionStore store = new JdbcMultiProductQuoteSessionStore(
                jdbc, new ObjectMapper()
        );
        ResolvedQuote quote = quote(now);
        MultiProductQuoteSession session = new MultiProductQuoteSession(
                UUID.randomUUID(), userId, now, now.plusSeconds(900), List.of(
                new MultiProductQuoteItem(0, 111, "Первый", MultiProductQuoteItem.Status.AVAILABLE, Optional.of(quote)),
                new MultiProductQuoteItem(1, 222, "Недоступный", MultiProductQuoteItem.Status.UNAVAILABLE, Optional.empty())
        ));

        store.save(session);

        MultiProductQuoteSession loaded = store.findOwned(session.getId(), userId).orElseThrow();
        assertThat(loaded.getItems()).extracting(MultiProductQuoteItem::getDisplayName)
                .containsExactly("Первый", "Недоступный");
        assertThat(loaded.getItems().getFirst().getQuote()).isPresent().get()
                .satisfies(loadedQuote -> {
                    assertThat(loadedQuote.getQuoteSnapshotId()).isEqualTo(quote.getQuoteSnapshotId());
                    assertThat(loadedQuote.getTitle()).contains("Первый");
                    assertThat(loadedQuote.getInterpretedPrice()).isEqualTo(quote.getInterpretedPrice());
                });
        assertThat(store.findOwned(session.getId(), UUID.randomUUID())).isEmpty();
    }

    private ResolvedQuote quote(Instant now) {
        return new ResolvedQuote(
                UUID.randomUUID(), UUID.randomUUID(), Marketplace.WILDBERRIES, 111,
                "https://www.wildberries.ru/catalog/111/detail.aspx",
                Optional.of("Первый"), Optional.of("Бренд"), ResolvedVariant.noVariant(),
                new InterpretedPrice(
                        Optional.of(RubleAmount.ofMinorUnits(100_00)), Optional.empty(),
                        Optional.of(PriceSource.PRODUCT), SnapshotStatus.REGULAR_PRICE
                ),
                moscow().toPriceContext(), now, now.plusSeconds(900)
        );
    }

    private DriverManagerDataSource dataSource(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()
        );
    }
}
