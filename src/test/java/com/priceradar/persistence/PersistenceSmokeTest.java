package com.priceradar.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.notification.domain.NotificationType;
import com.priceradar.notification.infrastructure.persistence.NotificationOutboxJpaRepository;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.telegram.application.PendingTargetPrice;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Autowired
    private ResolvedQuotePersistenceService quotePersistenceService;

    @Autowired
    private ResolvedQuoteService resolvedQuoteService;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private WatchTargetJpaRepository watchTargetRepository;

    @Autowired
    private PriceSnapshotJpaRepository snapshotRepository;

    @Autowired
    private NotificationOutboxJpaRepository notificationOutboxRepository;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PendingTargetPriceStore pendingTargetPriceStore;

    @BeforeEach
    void clearBusinessData() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    notification_outbox,
                    subscriptions,
                    price_snapshots,
                    telegram_pending_target_prices,
                    watch_targets,
                    products,
                    user_profiles
                CASCADE
                """);
    }

    @Test
    void startsContextWithFlywayAndPersistsProviderCooldown() {
        Instant updatedAt = Instant.parse("2026-07-12T10:00:00Z");
        Instant cooldownUntil = Instant.parse("2026-07-12T10:15:00Z");

        cooldownStore.saveCooldownUntil(
                Marketplace.WILDBERRIES,
                cooldownUntil,
                updatedAt
        );

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(cooldownStore.findCooldownUntil(Marketplace.WILDBERRIES))
                .contains(cooldownUntil);
    }

    @Test
    void createsAlreadyReachedThresholdEventInSubscriptionTransaction() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID watchTargetId = quotePersistenceService.save(quoteCommand(777777L, now.minusSeconds(5)));
        UserProfile user = userProfileService.getOrCreate(10001L, 10001L);

        SubscriptionCreationResult result = subscriptionService.createFromQuote(
                user.getId(),
                watchTargetId,
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(11_000L)),
                now
        );

        assertThat(result.isCreated()).isTrue();
        assertThat(result.isTargetAlreadyReached()).isTrue();
        assertThat(notificationOutboxRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getSubscriptionId())
                            .isEqualTo(result.getSubscription().orElseThrow().getId());
                    assertThat(event.getNotificationType())
                            .isEqualTo(NotificationType.TARGET_REACHED);
                    assertThat(event.getSnapshotId()).isNotNull();
                });
    }

    @Test
    void persistsPendingTargetPriceInputAcrossStoreCalls() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID watchTargetId = quotePersistenceService.save(quoteCommand(888888L, now.minusSeconds(5)));
        PendingTargetPrice pending = new PendingTargetPrice(
                20001L,
                20001L,
                watchTargetId,
                now.plus(15, ChronoUnit.MINUTES)
        );

        pendingTargetPriceStore.put(pending, now);

        assertThat(pendingTargetPriceStore.find(20001L, 20001L, now))
                .get()
                .extracting(PendingTargetPrice::getWatchTargetId)
                .isEqualTo(watchTargetId);
    }

    @Test
    void persistsResolvedQuoteIdempotentlyAndChecksTtlFromSnapshots() {
        Instant freshObservation = Instant.now().minus(1, ChronoUnit.MINUTES);
        ResolvedQuotePersistenceCommand freshQuote = quoteCommand(123456L, freshObservation);

        UUID firstTargetId = quotePersistenceService.save(freshQuote);
        UUID repeatedTargetId = quotePersistenceService.save(freshQuote);

        assertThat(repeatedTargetId).isEqualTo(firstTargetId);
        assertThat(resolvedQuoteService.isFresh(firstTargetId)).isTrue();
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(watchTargetRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.count()).isEqualTo(1);

        Instant expiredObservation = Instant.now().minus(16, ChronoUnit.MINUTES);
        ResolvedQuotePersistenceCommand expiredQuote = quoteCommand(654321L, expiredObservation);
        UUID expiredTargetId = quotePersistenceService.save(expiredQuote);

        assertThat(resolvedQuoteService.isFresh(expiredTargetId)).isFalse();
        assertThat(productRepository.count()).isEqualTo(2);
        assertThat(watchTargetRepository.count()).isEqualTo(2);
        assertThat(snapshotRepository.count()).isEqualTo(2);
    }

    private ResolvedQuotePersistenceCommand quoteCommand(long nmId, Instant observedAt) {
        PriceContext priceContext = new PriceContext("Moscow", 1259570991L, 30);
        ProviderPriceFields priceFields = new ProviderPriceFields(
                true,
                Optional.of(RubleAmount.ofMinorUnits(10_000L)),
                Optional.of(RubleAmount.ofMinorUnits(12_000L))
        );
        InterpretedPrice interpretedPrice = new PriceSemanticsService().interpret(priceFields);
        String variantKey = ResolvedVariant.noVariant().getVariantKey();
        MarketplaceProductDetails product = new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                String.valueOf(nmId),
                Optional.of("Test product"),
                Optional.of("Test brand"),
                List.of(),
                Map.of(variantKey, priceFields)
        );

        return new ResolvedQuotePersistenceCommand(
                product,
                nmId,
                "https://www.wildberries.ru/catalog/%d/detail.aspx".formatted(nmId),
                ResolvedVariant.noVariant(),
                priceContext,
                interpretedPrice,
                observedAt
        );
    }
}
