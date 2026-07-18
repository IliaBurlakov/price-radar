package com.priceradar.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.notification.domain.NotificationType;
import com.priceradar.notification.application.NotificationDeliveryStore;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.notification.application.PendingNotificationDelivery;
import com.priceradar.notification.infrastructure.persistence.NotificationOutboxJpaRepository;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.scheduler.application.DueWatchTarget;
import com.priceradar.scheduler.application.DueWatchTargetReader;
import com.priceradar.scheduler.application.ScheduledObservationStore;
import com.priceradar.scheduler.application.WatchTargetCheckTransaction;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotEntity;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.SubscriptionStore;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
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
    private NotificationDeliveryStore notificationDeliveryStore;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionStatisticsService subscriptionStatisticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PendingTargetPriceStore pendingTargetPriceStore;

    @Autowired
    private DueWatchTargetReader dueWatchTargetReader;

    @Autowired
    private ScheduledObservationStore scheduledObservationStore;

    @Autowired
    private SubscriptionStore subscriptionStore;

    @Autowired
    private NotificationDecisionService notificationDecisionService;

    @Autowired
    private NotificationOutboxWriter notificationOutboxWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

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
    void protectsAndSanitizesPublishedActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Realm\""));

        String authorization = basicAuthorization(
                "test-actuator",
                "test-only-actuator-password"
        );
        mockMvc.perform(get("/actuator/health").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/info").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").value("0.0.1-SNAPSHOT"));
        mockMvc.perform(get("/actuator/metrics").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
        mockMvc.perform(get("/actuator/env").header("Authorization", authorization))
                .andExpect(status().is4xxClientError());
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

        PendingNotificationDelivery pending = notificationDeliveryStore.findDue(now, 10)
                .getFirst();
        Instant claimUntil = now.plusSeconds(30);
        assertThat(pending.getType()).isEqualTo(NotificationType.TARGET_REACHED);
        assertThat(pending.getTargetPrice())
                .contains(RubleAmount.ofMinorUnits(11_000L));
        assertThat(notificationDeliveryStore.claim(
                pending.getOutboxId(),
                pending.getNextAttemptAt(),
                now,
                claimUntil
        )).isTrue();
        assertThat(notificationDeliveryStore.markSent(
                pending.getOutboxId(),
                claimUntil,
                now.plusSeconds(1)
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification_outbox WHERE id = ?",
                String.class,
                pending.getOutboxId()
        )).isEqualTo("SENT");
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
    void processesOneSharedDueWatchTargetForMultipleSubscriptions() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID watchTargetId = quotePersistenceService.save(quoteCommand(999999L, now.minusSeconds(5)));
        jdbcTemplate.update(
                "UPDATE watch_targets SET next_check_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(1)),
                watchTargetId
        );

        assertThat(dueWatchTargetReader.findDue(now, 10)).isEmpty();

        UserProfile firstUser = userProfileService.getOrCreate(30001L, 30001L);
        UserProfile secondUser = userProfileService.getOrCreate(30002L, 30002L);
        subscriptionService.createFromQuote(
                firstUser.getId(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );
        subscriptionService.createFromQuote(
                secondUser.getId(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        List<DueWatchTarget> dueTargets = dueWatchTargetReader.findDue(now, 10);
        assertThat(dueTargets)
                .singleElement()
                .extracting(DueWatchTarget::getWatchTargetId)
                .isEqualTo(watchTargetId);
        DueWatchTarget dueTarget = dueTargets.getFirst();

        Instant observedAt = now.plusSeconds(1);
        Instant completedAt = now.plusSeconds(2);
        Instant nextCheckAt = completedAt.plus(6, ChronoUnit.HOURS);
        ProviderPriceFields scheduledFields = new ProviderPriceFields(
                true,
                Optional.of(RubleAmount.ofMinorUnits(9_000L)),
                Optional.of(RubleAmount.ofMinorUnits(12_000L))
        );
        MarketplaceProductDetails scheduledProduct = new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                "999999",
                Optional.of("Test product"),
                Optional.of("Test brand"),
                List.of(),
                Map.of("NO_VARIANT", scheduledFields)
        );
        WatchTargetCheckTransaction checkTransaction = new WatchTargetCheckTransaction(
                scheduledObservationStore,
                subscriptionStore,
                notificationDecisionService,
                notificationOutboxWriter
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                checkTransaction.persistObservation(
                        dueTarget,
                        UUID.randomUUID(),
                        scheduledProduct,
                        new PriceSemanticsService().interpret(scheduledFields),
                        observedAt,
                        completedAt,
                        nextCheckAt
                ));

        assertThat(snapshotRepository.count()).isEqualTo(2);
        assertThat(notificationOutboxRepository.count()).isEqualTo(2);
        assertThat(watchTargetRepository.findById(watchTargetId).orElseThrow().getNextCheckAt())
                .isEqualTo(nextCheckAt);
    }

    @Test
    void calculatesStatisticsOnlyForTheCurrentSubscriptionPeriod() {
        Instant subscriptionStartedAt = Instant.parse("2026-07-18T10:00:00Z");
        UUID watchTargetId = quotePersistenceService.save(
                quoteCommand(444444L, subscriptionStartedAt.minusSeconds(1))
        );
        UserProfile user = userProfileService.getOrCreate(40001L, 40001L);
        SubscriptionCreationResult firstSubscription = subscriptionService.createFromQuote(
                user.getId(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                subscriptionStartedAt
        );

        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(1),
                SnapshotStatus.REGULAR_PRICE,
                PriceSource.PRODUCT,
                9_000L,
                null,
                true
        );
        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(2),
                SnapshotStatus.REGULAR_PRICE,
                PriceSource.PRODUCT,
                11_001L,
                null,
                true
        );
        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(3),
                SnapshotStatus.BASIC_FALLBACK,
                PriceSource.BASIC_FALLBACK,
                null,
                7_000L,
                true
        );
        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(4),
                SnapshotStatus.UNAVAILABLE,
                null,
                null,
                null,
                false
        );
        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(5),
                SnapshotStatus.NO_PRICE,
                null,
                null,
                null,
                true
        );

        UUID firstSubscriptionId = firstSubscription.getSubscription()
                .orElseThrow()
                .getId();
        SubscriptionStatistics firstPeriod = subscriptionStatisticsService.calculate(
                user.getId(),
                firstSubscriptionId,
                StatisticsPeriod.ALL_TIME,
                subscriptionStartedAt.plusSeconds(6)
        ).orElseThrow();

        assertThat(firstPeriod.getEffectivePeriodStart()).isEqualTo(subscriptionStartedAt);
        assertThat(firstPeriod.getObservationCount()).isEqualTo(2);
        assertThat(firstPeriod.getMinimumPrice())
                .contains(RubleAmount.ofMinorUnits(9_000L));
        assertThat(firstPeriod.getMaximumPrice())
                .contains(RubleAmount.ofMinorUnits(11_001L));
        assertThat(firstPeriod.getAverageMinorUnits().orElseThrow())
                .isEqualByComparingTo("10000.5");

        subscriptionService.end(
                user.getId(),
                firstSubscriptionId,
                subscriptionStartedAt.plusSeconds(7)
        );
        SubscriptionCreationResult secondSubscription = subscriptionService.createFromQuote(
                user.getId(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                subscriptionStartedAt.plusSeconds(8)
        );
        UUID secondSubscriptionId = secondSubscription.getSubscription()
                .orElseThrow()
                .getId();

        SubscriptionStatistics newPeriodWithoutObservations = subscriptionStatisticsService
                .calculate(
                        user.getId(),
                        secondSubscriptionId,
                        StatisticsPeriod.ALL_TIME,
                        subscriptionStartedAt.plusSeconds(9)
                ).orElseThrow();

        assertThat(newPeriodWithoutObservations.hasData()).isFalse();
        assertThat(newPeriodWithoutObservations.getObservationCount()).isZero();
        assertThat(newPeriodWithoutObservations.getMinimumPrice()).isEmpty();

        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(10),
                SnapshotStatus.REGULAR_PRICE,
                PriceSource.PRODUCT,
                8_500L,
                null,
                true
        );
        SubscriptionStatistics newPeriod = subscriptionStatisticsService.calculate(
                user.getId(),
                secondSubscriptionId,
                StatisticsPeriod.ALL_TIME,
                subscriptionStartedAt.plusSeconds(11)
        ).orElseThrow();

        assertThat(newPeriod.getObservationCount()).isOne();
        assertThat(newPeriod.getMinimumPrice())
                .contains(RubleAmount.ofMinorUnits(8_500L));
        assertThat(newPeriod.getAverageMinorUnits().orElseThrow())
                .isEqualByComparingTo("8500");
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

    private void saveSnapshot(
            UUID watchTargetId,
            Instant observedAt,
            SnapshotStatus status,
            PriceSource priceSource,
            Long regularPriceMinor,
            Long marketingBasePriceMinor,
            boolean available
    ) {
        snapshotRepository.save(new PriceSnapshotEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                observedAt,
                status,
                priceSource,
                regularPriceMinor,
                marketingBasePriceMinor,
                available
        ));
    }

    private String basicAuthorization(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );
    }
}
