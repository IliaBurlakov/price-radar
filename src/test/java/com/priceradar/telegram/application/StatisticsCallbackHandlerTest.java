package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.application.ObservedPriceStatistics;
import com.priceradar.statistics.application.SubscriptionStatistics;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;

@ExtendWith(MockitoExtension.class)
class StatisticsCallbackHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");
    private static final long TELEGRAM_USER_ID = 7001L;
    private static final long CHAT_ID = 7001L;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private SubscriptionStatisticsService statisticsService;

    @Mock
    private TelegramGateway telegramGateway;

    private UserProfile profile;
    private StatisticsCallbackHandler handler;

    @BeforeEach
    void setUp() {
        profile = new UserProfile(
                UUID.randomUUID(),
                TELEGRAM_USER_ID,
                CHAT_ID,
                moscow(),
                UserPricePreferences.defaults()
        );
        handler = new StatisticsCallbackHandler(
                userProfileService,
                statisticsService,
                new StatisticsMessageFactory(new WalletEstimateService()),
                telegramGateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rendersSubscriptionStatisticsWithRegionAndApproximatePriceWarning() {
        when(userProfileService.getOrCreate(TELEGRAM_USER_ID, CHAT_ID))
                .thenReturn(profile);
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                subscriptionId,
                StatisticsPeriod.LAST_30_DAYS,
                NOW.minusSeconds(86_400),
                NOW,
                ObservedPriceStatistics.of(
                        35,
                        RubleAmount.ofMinorUnits(22_800L),
                        Instant.parse("2026-07-18T08:40:00Z"),
                        RubleAmount.ofMinorUnits(49_600L),
                        new BigDecimal("43900"),
                        RubleAmount.ofMinorUnits(49_600L),
                        RubleAmount.ofMinorUnits(43_400L)
                )
        );
        when(statisticsService.calculate(
                profile.getId(),
                subscriptionId,
                StatisticsPeriod.LAST_30_DAYS,
                NOW
        )).thenReturn(Optional.of(statistics));
        IncomingTelegramCallback callback = callback(
                StatisticsCallbackData.encode(
                        subscriptionId,
                        StatisticsPeriod.LAST_30_DAYS
                )
        );

        assertThat(handler.handleCallback(callback)).isTrue();

        ArgumentCaptor<OutgoingTelegramMessage> messageCaptor =
                ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .isEqualTo("""
                        📊 Статистика за 30 дней
                        🗓 17.07.2026 — 18.07.2026

                        💰 Последняя известная цена
                        434 ₽ · с WB Кошельком 420 ₽

                        📉 Изменение за период
                        496 ₽ → 434 ₽
                        −62 ₽ (−12,5%)

                        🏷 Минимум
                        228 ₽ · с WB Кошельком 221 ₽
                        Зафиксирован: 18.07.2026 11:40 МСК
                        Сейчас цена на 206 ₽ выше минимума.

                        📌 За период
                        Максимум: 496 ₽
                        Средняя: 439 ₽
                        Проверок цены: 35

                        🌍 Город: Москва

                        ℹ️ WB Кошелёк: скидка 3%.
                        История ведётся с момента добавления товара.""");
        assertThat(messageCaptor.getValue().getInlineKeyboard()).isNotEmpty();
        assertThat(messageCaptor.getValue().getInlineKeyboard().stream()
                .flatMap(java.util.Collection::stream)
                .filter(button -> button.getText().equals("← Назад"))
                .map(TelegramInlineButton::getCallbackData)
                .map(data -> SubscriptionCallbackData.parse(
                        SubscriptionCallbackData.Action.OPEN_ITEM, data
                ))
                .toList())
                .containsExactly(Optional.of(subscriptionId));
    }

    @Test
    void periodMenuBackReturnsToTheSameTrackedItem() {
        when(userProfileService.getOrCreate(TELEGRAM_USER_ID, CHAT_ID))
                .thenReturn(profile);
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                subscriptionId,
                StatisticsPeriod.ALL_TIME,
                NOW.minusSeconds(60),
                NOW,
                ObservedPriceStatistics.empty()
        );
        when(statisticsService.calculate(
                profile.getId(), subscriptionId, StatisticsPeriod.ALL_TIME, NOW
        )).thenReturn(Optional.of(statistics));

        handler.handleCallback(callback(SubscriptionCallbackData.encode(
                SubscriptionCallbackData.Action.SHOW_STATISTICS,
                subscriptionId
        )));

        ArgumentCaptor<OutgoingTelegramMessage> messageCaptor =
                ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());
        TelegramInlineButton back = messageCaptor.getValue().getInlineKeyboard().stream()
                .flatMap(java.util.Collection::stream)
                .filter(button -> button.getText().equals("← Назад"))
                .findFirst()
                .orElseThrow();
        assertThat(SubscriptionCallbackData.parse(
                SubscriptionCallbackData.Action.OPEN_ITEM,
                back.getCallbackData()
        )).contains(subscriptionId);
    }

    @Test
    void reportsNoDataWithoutFabricatedPriceValues() {
        when(userProfileService.getOrCreate(TELEGRAM_USER_ID, CHAT_ID))
                .thenReturn(profile);
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                subscriptionId,
                StatisticsPeriod.ALL_TIME,
                NOW.minusSeconds(60),
                NOW,
                ObservedPriceStatistics.empty()
        );
        when(statisticsService.calculate(
                profile.getId(),
                subscriptionId,
                StatisticsPeriod.ALL_TIME,
                NOW
        )).thenReturn(Optional.of(statistics));

        handler.handleCallback(callback(StatisticsCallbackData.encode(
                subscriptionId,
                StatisticsPeriod.ALL_TIME
        )));

        ArgumentCaptor<OutgoingTelegramMessage> messageCaptor =
                ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .isEqualTo("""
                        📊 Статистика за всё время отслеживания
                        🗓 18.07.2026 — 18.07.2026

                        За этот период пока недостаточно данных.

                        🌍 Город: Москва

                        История ведётся с момента добавления товара.""");
        assertThat(messageCaptor.getValue().getInlineKeyboard()).isNotEmpty();
    }

    @Test
    void reportsWhenLatestKnownPriceIsThePeriodMinimum() {
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                UUID.randomUUID(),
                StatisticsPeriod.LAST_7_DAYS,
                NOW.minusSeconds(86_400),
                NOW,
                ObservedPriceStatistics.of(
                        2,
                        RubleAmount.ofMinorUnits(41_000L),
                        NOW.minusSeconds(60),
                        RubleAmount.ofMinorUnits(43_400L),
                        new BigDecimal("42200"),
                        RubleAmount.ofMinorUnits(41_000L),
                        RubleAmount.ofMinorUnits(41_000L)
                )
        );

        OutgoingTelegramMessage message = new StatisticsMessageFactory(
                new WalletEstimateService()
        ).create(
                CHAT_ID,
                statistics,
                "Moscow",
                UserPricePreferences.defaults()
        );

        assertThat(message.getText())
                .contains("➖ Изменение за период")
                .contains("Последняя известная цена — минимальная за выбранный период.")
                .doesNotContain("выше минимума");
    }

    @Test
    void usesGrowthIconWhenLatestKnownPriceIncreased() {
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                UUID.randomUUID(),
                StatisticsPeriod.LAST_7_DAYS,
                NOW.minusSeconds(86_400),
                NOW,
                ObservedPriceStatistics.of(
                        2,
                        RubleAmount.ofMinorUnits(41_000L),
                        NOW.minusSeconds(120),
                        RubleAmount.ofMinorUnits(49_600L),
                        new BigDecimal("45300"),
                        RubleAmount.ofMinorUnits(41_000L),
                        RubleAmount.ofMinorUnits(49_600L)
                )
        );

        OutgoingTelegramMessage message = new StatisticsMessageFactory(
                new WalletEstimateService()
        ).create(
                CHAT_ID,
                statistics,
                "Moscow",
                UserPricePreferences.defaults()
        );

        assertThat(message.getText())
                .contains("📈 Изменение за период")
                .doesNotContain("📉 Изменение за период")
                .doesNotContain("➖ Изменение за период");
    }

    private IncomingTelegramCallback callback(String data) {
        return new IncomingTelegramCallback(
                "statistics-callback",
                TELEGRAM_USER_ID,
                CHAT_ID,
                "private",
                data
        );
    }
}
