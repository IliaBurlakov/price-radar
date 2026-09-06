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
import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.notification.application.NotificationDecisionResult;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.domain.NotificationType;
import com.priceradar.notification.domain.OutboxStatus;
import com.priceradar.notification.infrastructure.persistence.NotificationOutboxJpaRepository;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.product.application.PersistedResolvedQuote;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.scheduler.application.DueWatchTarget;
import com.priceradar.scheduler.application.DueWatchTargetReader;
import com.priceradar.scheduler.application.NotificationFanOutService;
import com.priceradar.scheduler.application.WatchTargetCheckTransaction;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.sharedbasket.application.PendingSharedBasketImport;
import com.priceradar.region.application.RegionChangeResult;
import com.priceradar.region.application.UserRegionService;
import com.priceradar.sharedbasket.application.PendingSharedBasketImportStore;
import com.priceradar.sharedbasket.application.PendingSharedBasketItem;
import com.priceradar.sharedbasket.application.PendingUnavailableSharedBasketItem;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotEntity;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import com.priceradar.tracking.application.SubscriptionCreationResult;
import com.priceradar.tracking.application.SubscriptionConditionChangeResult;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.telegram.application.PendingTargetPrice;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.infrastructure.persistence.UserProfileJpaRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
    private UserRegionService userRegionService;

    @Autowired
    private UserProfileJpaRepository userProfileRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionStore subscriptionStore;

    @Autowired
    private SubscriptionStatisticsService subscriptionStatisticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PendingTargetPriceStore pendingTargetPriceStore;

    @Autowired
    private PendingSharedBasketImportStore pendingSharedBasketImportStore;

    @Autowired
    private DueWatchTargetReader dueWatchTargetReader;

    @Autowired
    private WatchTargetCheckTransaction watchTargetCheckTransaction;

    @Autowired
    private NotificationFanOutService notificationFanOutService;

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

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(cooldownStore.findCooldownUntil(Marketplace.WILDBERRIES))
                .contains(cooldownUntil);
    }

    @Test
    @Transactional
    void persistsInitialRegionSelectionAndDoesNotResetItOnUpsert() {
        long telegramId = 22001L;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UserProfile created = userProfileService.getOrCreate(telegramId, telegramId);
        assertThat(created.isRegionSelected()).isFalse();

        RegionChangeResult selected = userRegionService.changeLocation(
                created.getId(), com.priceradar.testsupport.TestMarketplaceRegions.moscow(), now
        );
        assertThat(selected.getStatus()).isEqualTo(RegionChangeResult.Status.SELECTED);
        assertThat(userProfileService.getOrCreate(telegramId, telegramId).isRegionSelected())
                .isTrue();

        userProfileRepository.upsert(
                UUID.randomUUID(),
                telegramId,
                telegramId + 1,
                3,
                10,
                now.plusSeconds(1),
                now.plusSeconds(1)
        );

        UserProfile afterUpsert = userProfileService.getOrCreate(telegramId, telegramId + 1);
        assertThat(afterUpsert.isRegionSelected()).isTrue();
        assertThat(afterUpsert.getTelegramChatId()).isEqualTo(telegramId + 1);
    }

    @Test
    void persistsPendingSharedBasketImportAcrossStoreCalls() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PersistedResolvedQuote firstQuote = quotePersistenceService.save(
                quoteCommand(787878L, now.minusSeconds(5))
        );
        PersistedResolvedQuote secondQuote = quotePersistenceService.save(
                quoteCommand(787879L, now.minusSeconds(4))
        );
        UserProfile user = userProfileService.getOrCreate(21001L, 21001L);
        UserProfile anotherUser = userProfileService.getOrCreate(21002L, 21002L);
        PendingSharedBasketImport pendingImport = new PendingSharedBasketImport(
                UUID.randomUUID(), user.getId(), com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 3, 2, 0,
                List.of(new PendingUnavailableSharedBasketItem(0, "Unavailable — XXL")),
                List.of(
                        new PendingSharedBasketItem(
                                1, secondQuote.getWatchTargetId(), secondQuote.getSnapshotId(), Optional.of("Second")
                        ),
                        new PendingSharedBasketItem(
                                0, firstQuote.getWatchTargetId(), firstQuote.getSnapshotId(), Optional.of("First")
                        )
                ),
                now, now.plus(15, ChronoUnit.MINUTES)
        );

        pendingSharedBasketImportStore.save(pendingImport, now);

        assertThat(pendingSharedBasketImportStore.findOwned(pendingImport.getId(), user.getId()))
                .get()
                .satisfies(restored -> {
                    assertThat(restored.getExpiresAt()).isEqualTo(pendingImport.getExpiresAt());
                    assertThat(restored.getAvailableItems()).isEqualTo(2);
                    assertThat(restored.getUnresolvedItems()).isZero();
                    assertThat(restored.getUnavailableItems())
                            .extracting(PendingUnavailableSharedBasketItem::getDisplayName)
                            .containsExactly("Unavailable — XXL");
                    assertThat(restored.getItems())
                            .extracting(PendingSharedBasketItem::getPosition)
                            .containsExactly(0, 1);
                    assertThat(restored.getItems())
                            .extracting(PendingSharedBasketItem::getWatchTargetId)
                            .containsExactly(firstQuote.getWatchTargetId(), secondQuote.getWatchTargetId());
                });
        assertThat(pendingSharedBasketImportStore.findOwned(pendingImport.getId(), anotherUser.getId()))
                .isEmpty();

        PendingSharedBasketImport replacement = new PendingSharedBasketImport(
                UUID.randomUUID(), user.getId(), com.priceradar.testsupport.TestMarketplaceRegions.MOSCOW_ID, 1, 1, 0, List.of(),
                List.of(new PendingSharedBasketItem(
                        0, secondQuote.getWatchTargetId(), secondQuote.getSnapshotId(), Optional.of("Replacement")
                )),
                now.plusSeconds(1), now.plus(16, ChronoUnit.MINUTES)
        );
        pendingSharedBasketImportStore.save(replacement, now.plusSeconds(1));

        assertThat(pendingSharedBasketImportStore.findOwned(pendingImport.getId(), user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pending_shared_basket_import_items WHERE import_id = ?",
                Long.class,
                pendingImport.getId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pending_shared_basket_unavailable_items WHERE import_id = ?",
                Long.class,
                pendingImport.getId()
        )).isZero();

        pendingSharedBasketImportStore.remove(replacement.getId(), user.getId());

        assertThat(pendingSharedBasketImportStore.findOwned(replacement.getId(), user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pending_shared_basket_import_items WHERE import_id = ?",
                Long.class,
                replacement.getId()
        )).isZero();
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
    void createsAlreadyReachedThresholdWithOnePendingOutboxEvent() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(777777L, now.minusSeconds(5))
        );
        UserProfile user = userProfileService.getOrCreate(10001L, 10001L);

        SubscriptionCreationResult result = subscriptionService.createFromQuote(
                user.getId(),
                quote.getSnapshotId(),
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(11_000L)),
                now
        );

        assertThat(result.isCreated()).isTrue();
        assertThat(result.isTargetAlreadyReached()).isTrue();
        assertThat(notificationOutboxRepository.findAll())
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getSubscriptionId())
                            .isEqualTo(result.getSubscription().orElseThrow().getId());
                    assertThat(outbox.getSnapshotId()).isEqualTo(quote.getSnapshotId());
                    assertThat(outbox.getNotificationType()).isEqualTo(NotificationType.TARGET_REACHED);
                    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
                });
    }

    @Test
    void persistsPendingTargetPriceInputAcrossStoreCalls() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(888888L, now.minusSeconds(5))
        );
        PendingTargetPrice pending = new PendingTargetPrice(
                20001L,
                20001L,
                quote.getSnapshotId(),
                now.plus(15, ChronoUnit.MINUTES)
        );

        pendingTargetPriceStore.put(pending, now);

        assertThat(pendingTargetPriceStore.find(20001L, 20001L, now))
                .get()
                .extracting(PendingTargetPrice::getQuoteSnapshotId)
                .isEqualTo(quote.getSnapshotId());

        UserProfile user = userProfileService.getOrCreate(20001L, 20001L);
        userRegionService.changeLocation(
                user.getId(), com.priceradar.testsupport.TestMarketplaceRegions.moscow(), now
        );
        Subscription subscription = subscriptionService.createFromQuote(
                user.getId(),
                quote.getSnapshotId(),
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        ).getSubscription().orElseThrow();
        PendingTargetPrice edit = new PendingTargetPrice(
                20001L,
                20001L,
                PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION,
                subscription.getId(),
                now.plus(15, ChronoUnit.MINUTES)
        );

        pendingTargetPriceStore.put(edit, now);

        assertThat(pendingTargetPriceStore.find(20001L, 20001L, now))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getPurpose())
                            .isEqualTo(PendingTargetPrice.Purpose.EDIT_SUBSCRIPTION);
                    assertThat(saved.requireSubscriptionId()).isEqualTo(subscription.getId());
                });
    }

    @Test
    void changesConditionInPlaceAndPreservesSubscriptionStatistics() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(889999L, createdAt.minusSeconds(5))
        );
        UserProfile user = userProfileService.getOrCreate(20002L, 20002L);
        userRegionService.changeLocation(
                user.getId(), com.priceradar.testsupport.TestMarketplaceRegions.moscow(), createdAt
        );
        Subscription original = subscriptionService.createFromQuote(
                user.getId(), quote.getSnapshotId(), NotificationMode.ANY_DECREASE,
                Optional.empty(), createdAt
        ).getSubscription().orElseThrow();
        saveSnapshot(
                quote.getWatchTargetId(), createdAt.plusSeconds(60),
                SnapshotStatus.REGULAR_PRICE, PriceSource.PRODUCT,
                35_000L, null, true
        );
        saveSnapshot(
                quote.getWatchTargetId(), createdAt.plusSeconds(120),
                SnapshotStatus.REGULAR_PRICE, PriceSource.PRODUCT,
                30_000L, null, true
        );
        saveSnapshot(
                quote.getWatchTargetId(), createdAt.plusSeconds(180),
                SnapshotStatus.REGULAR_PRICE, PriceSource.PRODUCT,
                36_000L, null, true
        );
        Instant statisticsAt = createdAt.plusSeconds(240);
        SubscriptionStatistics before = subscriptionStatisticsService.calculate(
                user.getId(), original.getId(), StatisticsPeriod.ALL_TIME, statisticsAt
        ).orElseThrow();

        SubscriptionConditionChangeResult target = subscriptionService.changeToTargetPrice(
                user.getId(), original.getId(), RubleAmount.ofMinorUnits(28_000), statisticsAt
        );
        SubscriptionStatistics afterTarget = subscriptionStatisticsService.calculate(
                user.getId(), original.getId(), StatisticsPeriod.ALL_TIME, statisticsAt
        ).orElseThrow();
        SubscriptionConditionChangeResult minimum = subscriptionService.changeToAnyDecrease(
                user.getId(), original.getId(), statisticsAt.plusSeconds(60)
        );
        SubscriptionStatistics afterMinimum = subscriptionStatisticsService.calculate(
                user.getId(), original.getId(), StatisticsPeriod.ALL_TIME,
                statisticsAt.plusSeconds(60)
        ).orElseThrow();

        assertThat(target.getSubscription().orElseThrow().getId()).isEqualTo(original.getId());
        assertThat(minimum.getSubscription().orElseThrow().getId()).isEqualTo(original.getId());
        assertThat(minimum.getSubscription().orElseThrow().getCreatedAt())
                .isEqualTo(original.getCreatedAt());
        assertThat(minimum.getSubscription().orElseThrow().getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(30_000));
        assertThat(minimum.getSubscription().orElseThrow().getLastProcessedPriceObservedAt())
                .contains(createdAt.plusSeconds(180));
        assertThat(afterTarget.getMinimumPrice()).isEqualTo(before.getMinimumPrice());
        assertThat(afterTarget.getFirstPrice()).isEqualTo(before.getFirstPrice());
        assertThat(afterTarget.getLatestPrice()).isEqualTo(before.getLatestPrice());
        assertThat(afterMinimum.getMinimumPrice()).isEqualTo(before.getMinimumPrice());
        assertThat(afterMinimum.getFirstPrice()).isEqualTo(before.getFirstPrice());
        assertThat(afterMinimum.getLatestPrice()).isEqualTo(before.getLatestPrice());
        assertThat(notificationOutboxRepository.count()).isZero();
    }

    @Test
    void processesOneSharedDueWatchTargetForMultipleSubscriptions() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(999999L, now.minusSeconds(5))
        );
        UUID watchTargetId = quote.getWatchTargetId();
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
                quote.getSnapshotId(),
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );
        subscriptionService.createFromQuote(
                secondUser.getId(),
                quote.getSnapshotId(),
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
        NotificationObservation observation = watchTargetCheckTransaction.persistObservation(
                dueTarget,
                UUID.randomUUID(),
                scheduledProduct,
                new PriceSemanticsService().interpret(scheduledFields),
                observedAt,
                completedAt,
                nextCheckAt
        );
        notificationFanOutService.process(observation, completedAt);

        assertThat(snapshotRepository.count()).isEqualTo(2);
        assertThat(notificationOutboxRepository.count()).isEqualTo(2);
        assertThat(watchTargetRepository.findById(watchTargetId).orElseThrow().getNextCheckAt())
                .isEqualTo(nextCheckAt);
    }

    @Test
    void importedInitialPriceBelongsOnlyToItsSubscriptionHistoryPeriod() {
        Instant importedAt = Instant.parse("2026-08-24T09:55:00Z");
        Instant subscribedAt = importedAt.plusSeconds(300);
        PersistedResolvedQuote importedQuote = quotePersistenceService.save(
                quoteCommand(445445L, importedAt, 44_500L)
        );
        UserProfile user = userProfileService.getOrCreate(44501L, 44501L);
        Subscription first = subscriptionStore.create(new Subscription(
                UUID.randomUUID(), user.getId(), importedQuote.getWatchTargetId(),
                NotificationMode.ANY_DECREASE, Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(44_500L)), Optional.of(importedAt),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(), SubscriptionStatus.ACTIVE,
                subscribedAt, importedAt, Optional.empty(), 0
        ));

        Instant lowerPriceObservedAt = subscribedAt.plusSeconds(60);
        saveSnapshot(
                importedQuote.getWatchTargetId(), lowerPriceObservedAt,
                SnapshotStatus.REGULAR_PRICE, PriceSource.PRODUCT,
                33_100L, null, true
        );
        SubscriptionStatistics firstPeriod = subscriptionStatisticsService.calculate(
                user.getId(), first.getId(), StatisticsPeriod.ALL_TIME,
                lowerPriceObservedAt.plusSeconds(1)
        ).orElseThrow();
        NotificationDecisionResult decision = new NotificationDecisionService().evaluate(
                first,
                new NotificationObservation(
                        UUID.randomUUID(), importedQuote.getWatchTargetId(),
                        new PriceSemanticsService().interpret(new ProviderPriceFields(
                                true, Optional.of(RubleAmount.ofMinorUnits(33_100L)), Optional.empty()
                        )),
                        lowerPriceObservedAt
                )
        );

        assertThat(first.getCreatedAt()).isEqualTo(subscribedAt);
        assertThat(first.getPriceHistoryStartedAt()).isEqualTo(importedAt);
        assertThat(firstPeriod.getObservationCount()).isEqualTo(2);
        assertThat(firstPeriod.getFirstPrice()).contains(RubleAmount.ofMinorUnits(44_500L));
        assertThat(firstPeriod.getLatestPrice()).contains(RubleAmount.ofMinorUnits(33_100L));
        assertThat(decision.getNotificationIntent()).get().satisfies(intent -> {
            assertThat(intent.getPreviousPrice()).contains(RubleAmount.ofMinorUnits(44_500L));
            assertThat(intent.getCurrentPrice()).isEqualTo(RubleAmount.ofMinorUnits(33_100L));
        });

        subscriptionService.end(
                user.getId(), first.getId(), lowerPriceObservedAt.plusSeconds(1)
        );
        Instant secondImportAt = lowerPriceObservedAt.plusSeconds(10);
        PersistedResolvedQuote secondQuote = quotePersistenceService.save(
                quoteCommand(445445L, secondImportAt, 40_000L)
        );
        Subscription second = subscriptionStore.create(new Subscription(
                UUID.randomUUID(), user.getId(), secondQuote.getWatchTargetId(),
                NotificationMode.ANY_DECREASE, Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(40_000L)), Optional.of(secondImportAt),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(), SubscriptionStatus.ACTIVE,
                secondImportAt.plusSeconds(5), secondImportAt, Optional.empty(), 0
        ));
        SubscriptionStatistics secondPeriod = subscriptionStatisticsService.calculate(
                user.getId(), second.getId(), StatisticsPeriod.ALL_TIME,
                secondImportAt.plusSeconds(6)
        ).orElseThrow();

        assertThat(secondPeriod.getObservationCount()).isOne();
        assertThat(secondPeriod.getFirstPrice()).contains(RubleAmount.ofMinorUnits(40_000L));
        assertThat(secondPeriod.getLatestPrice()).contains(RubleAmount.ofMinorUnits(40_000L));
    }

    @Test
    void directQuoteInitialPriceBelongsToSubscriptionStatistics() {
        Instant quotedAt = Instant.parse("2026-08-24T09:55:00Z");
        Instant subscribedAt = quotedAt.plusSeconds(5);
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(445446L, quotedAt, 44_500L)
        );
        UserProfile user = userProfileService.getOrCreate(44502L, 44502L);
        userRegionService.changeLocation(
                user.getId(), com.priceradar.testsupport.TestMarketplaceRegions.moscow(), quotedAt
        );

        Subscription subscription = subscriptionService.createFromQuote(
                user.getId(), quote.getSnapshotId(), NotificationMode.ANY_DECREASE,
                Optional.empty(), subscribedAt
        ).getSubscription().orElseThrow();
        SubscriptionStatistics initialStatistics = subscriptionStatisticsService.calculate(
                user.getId(), subscription.getId(), StatisticsPeriod.ALL_TIME,
                subscribedAt
        ).orElseThrow();

        assertThat(subscription.getCreatedAt()).isEqualTo(subscribedAt);
        assertThat(subscription.getPriceHistoryStartedAt()).isEqualTo(quotedAt);
        assertThat(initialStatistics.getObservationCount()).isOne();
        assertThat(initialStatistics.getFirstPrice())
                .contains(RubleAmount.ofMinorUnits(44_500L));
        assertThat(initialStatistics.getLatestPrice())
                .contains(RubleAmount.ofMinorUnits(44_500L));
        assertThat(initialStatistics.getMinimumPrice())
                .contains(RubleAmount.ofMinorUnits(44_500L));
        assertThat(initialStatistics.getMaximumPrice())
                .contains(RubleAmount.ofMinorUnits(44_500L));

        Instant lowerPriceObservedAt = subscribedAt.plusSeconds(60);
        saveSnapshot(
                quote.getWatchTargetId(), lowerPriceObservedAt,
                SnapshotStatus.REGULAR_PRICE, PriceSource.PRODUCT,
                33_100L, null, true
        );
        SubscriptionStatistics updatedStatistics = subscriptionStatisticsService.calculate(
                user.getId(), subscription.getId(), StatisticsPeriod.ALL_TIME,
                lowerPriceObservedAt.plusSeconds(1)
        ).orElseThrow();

        assertThat(updatedStatistics.getObservationCount()).isEqualTo(2);
        assertThat(updatedStatistics.getFirstPrice())
                .contains(RubleAmount.ofMinorUnits(44_500L));
        assertThat(updatedStatistics.getLatestPrice())
                .contains(RubleAmount.ofMinorUnits(33_100L));
    }

    @Test
    void calculatesStatisticsOnlyForTheCurrentSubscriptionPeriod() {
        Instant subscriptionStartedAt = Instant.parse("2026-07-18T10:00:00Z");
        PersistedResolvedQuote quote = quotePersistenceService.save(
                quoteCommand(444444L, subscriptionStartedAt.minusSeconds(1))
        );
        UUID watchTargetId = quote.getWatchTargetId();
        UserProfile user = userProfileService.getOrCreate(40001L, 40001L);
        userRegionService.changeLocation(
                user.getId(),
                com.priceradar.testsupport.TestMarketplaceRegions.moscow(),
                subscriptionStartedAt
        );
        SubscriptionCreationResult firstSubscription = subscriptionService.createFromQuote(
                user.getId(),
                quote.getSnapshotId(),
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

        assertThat(firstPeriod.getEffectivePeriodStart())
                .isEqualTo(subscriptionStartedAt.minusSeconds(1));
        assertThat(firstPeriod.getObservationCount()).isEqualTo(3);
        assertThat(firstPeriod.getMinimumPrice())
                .contains(RubleAmount.ofMinorUnits(9_000L));
        assertThat(firstPeriod.getMinimumObservedAt())
                .contains(subscriptionStartedAt.plusSeconds(1));
        assertThat(firstPeriod.getMaximumPrice())
                .contains(RubleAmount.ofMinorUnits(11_001L));
        assertThat(firstPeriod.getAverageMinorUnits().orElseThrow())
                .isEqualByComparingTo("10000.3333333333333333");
        assertThat(firstPeriod.getFirstPrice())
                .contains(RubleAmount.ofMinorUnits(10_000L));
        assertThat(firstPeriod.getLatestPrice())
                .contains(RubleAmount.ofMinorUnits(11_001L));
        assertThat(firstPeriod.getPriceChangeMinorUnits()).contains(1_001L);
        assertThat(firstPeriod.getPriceChangePercent().orElseThrow())
                .isEqualByComparingTo("10.01");
        assertThat(firstPeriod.getLatestPriceDifferenceFromMinimum())
                .contains(RubleAmount.ofMinorUnits(2_001L));

        subscriptionService.end(
                user.getId(),
                firstSubscriptionId,
                subscriptionStartedAt.plusSeconds(7)
        );
        Instant secondQuoteObservedAt = subscriptionStartedAt.plusSeconds(8);
        PersistedResolvedQuote secondQuote = quotePersistenceService.save(
                quoteCommand(444444L, secondQuoteObservedAt)
        );
        SubscriptionCreationResult secondSubscription = subscriptionService.createFromQuote(
                user.getId(),
                secondQuote.getSnapshotId(),
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                subscriptionStartedAt.plusSeconds(9)
        );
        UUID secondSubscriptionId = secondSubscription.getSubscription()
                .orElseThrow()
                .getId();

        SubscriptionStatistics newPeriodWithInitialQuote = subscriptionStatisticsService
                .calculate(
                        user.getId(),
                        secondSubscriptionId,
                        StatisticsPeriod.ALL_TIME,
                        subscriptionStartedAt.plusSeconds(10)
                ).orElseThrow();

        assertThat(newPeriodWithInitialQuote.getEffectivePeriodStart())
                .isEqualTo(secondQuoteObservedAt);
        assertThat(newPeriodWithInitialQuote.getObservationCount()).isOne();
        assertThat(newPeriodWithInitialQuote.getFirstPrice())
                .contains(RubleAmount.ofMinorUnits(10_000L));
        assertThat(newPeriodWithInitialQuote.getLatestPrice())
                .contains(RubleAmount.ofMinorUnits(10_000L));

        saveSnapshot(
                watchTargetId,
                subscriptionStartedAt.plusSeconds(11),
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
                subscriptionStartedAt.plusSeconds(12)
        ).orElseThrow();

        assertThat(newPeriod.getObservationCount()).isEqualTo(2);
        assertThat(newPeriod.getMinimumPrice())
                .contains(RubleAmount.ofMinorUnits(8_500L));
        assertThat(newPeriod.getFirstPrice())
                .contains(RubleAmount.ofMinorUnits(10_000L));
        assertThat(newPeriod.getLatestPrice())
                .contains(RubleAmount.ofMinorUnits(8_500L));
        assertThat(newPeriod.getPriceChangeMinorUnits()).contains(-1_500L);
        assertThat(newPeriod.getLatestPriceDifferenceFromMinimum())
                .contains(RubleAmount.ofMinorUnits(0));
        assertThat(newPeriod.getAverageMinorUnits().orElseThrow())
                .isEqualByComparingTo("9250");
    }

    @Test
    void persistsResolvedQuoteIdempotently() {
        Instant freshObservation = Instant.now().minus(1, ChronoUnit.MINUTES);
        ResolvedQuotePersistenceCommand freshQuote = quoteCommand(123456L, freshObservation);

        PersistedResolvedQuote first = quotePersistenceService.save(freshQuote);
        PersistedResolvedQuote repeated = quotePersistenceService.save(freshQuote);

        assertThat(repeated.getWatchTargetId()).isEqualTo(first.getWatchTargetId());
        assertThat(repeated.getSnapshotId()).isEqualTo(first.getSnapshotId());
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(watchTargetRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.count()).isEqualTo(1);
    }

    @Test
    void persistsIndividualActiveSubscriptionLimits() {
        UserProfile standard = userProfileService.getOrCreate(22002L, 22002L);
        UserProfile extended = userProfileService.getOrCreate(22003L, 22003L);

        assertThat(standard.getActiveSubscriptionLimit()).isEqualTo(10);
        assertThat(extended.getActiveSubscriptionLimit()).isEqualTo(10);

        jdbcTemplate.update(
                "UPDATE user_profiles SET active_subscription_limit = 100 WHERE id = ?",
                extended.getId()
        );

        assertThat(userProfileService.getOrCreate(22002L, 22002L).getActiveSubscriptionLimit())
                .isEqualTo(10);
        assertThat(userProfileService.getOrCreate(22003L, 22003L).getActiveSubscriptionLimit())
                .isEqualTo(100);
    }

    @Test
    void quoteMetadataUpdateKeepsTheNewestObservation() {
        long nmId = 123457L;
        Instant initialObservation = Instant.now()
                .minus(3, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MILLIS);
        Instant newestObservation = initialObservation.plus(2, ChronoUnit.MINUTES);
        Instant staleObservation = initialObservation.plus(1, ChronoUnit.MINUTES);

        quotePersistenceService.save(quoteCommand(
                nmId, initialObservation, "Initial URL", "Initial title", "Initial brand"
        ));
        quotePersistenceService.save(quoteCommand(
                nmId, newestObservation, "Newest URL", "Newest title", "Newest brand"
        ));
        quotePersistenceService.save(quoteCommand(
                nmId, staleObservation, "Stale URL", "Stale title", "Stale brand"
        ));

        assertThat(productRepository.findByMarketplaceAndExternalProductId(
                Marketplace.WILDBERRIES, nmId
        )).get().satisfies(product -> {
            assertThat(product.getCanonicalUrl()).isEqualTo("Newest URL");
            assertThat(product.getTitle()).isEqualTo("Newest title");
            assertThat(product.getBrand()).isEqualTo("Newest brand");
            assertThat(product.getMetadataUpdatedAt()).isEqualTo(newestObservation);
        });
    }

    private ResolvedQuotePersistenceCommand quoteCommand(long nmId, Instant observedAt) {
        return quoteCommand(
                nmId,
                observedAt,
                "https://www.wildberries.ru/catalog/%d/detail.aspx".formatted(nmId),
                "Test product",
                "Test brand"
        );
    }

    private ResolvedQuotePersistenceCommand quoteCommand(
            long nmId,
            Instant observedAt,
            long regularPriceMinor
    ) {
        PriceContext priceContext = new PriceContext("Moscow", 1259570991L, 30);
        ProviderPriceFields priceFields = new ProviderPriceFields(
                true,
                Optional.of(RubleAmount.ofMinorUnits(regularPriceMinor)),
                Optional.empty()
        );
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
                new PriceSemanticsService().interpret(priceFields),
                observedAt
        );
    }

    private ResolvedQuotePersistenceCommand quoteCommand(
            long nmId,
            Instant observedAt,
            String canonicalUrl,
            String title,
            String brand
    ) {
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
                Optional.of(title),
                Optional.of(brand),
                List.of(),
                Map.of(variantKey, priceFields)
        );

        return new ResolvedQuotePersistenceCommand(
                product,
                nmId,
                canonicalUrl,
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
