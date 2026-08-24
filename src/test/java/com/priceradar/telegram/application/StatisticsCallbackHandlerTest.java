package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.PriceContext;
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
                new PriceContext("Moscow", 1259570991L, 30),
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
                .contains("за последние 30 дней")
                .contains("Последняя известная цена: 434 ₽ · с WB Кошельком ≈ 421 ₽")
                .contains("496 ₽ → 434 ₽")
                .contains("−62 ₽ (−12,5%)")
                .contains("Минимум: 228 ₽ · с WB Кошельком ≈ 221 ₽")
                .contains("Зафиксирован: 18.07.2026 11:40 МСК")
                .contains("Последняя цена на 206 ₽ выше минимума")
                .contains("Максимум: 496 ₽")
                .contains("Средняя: 439 ₽")
                .contains("скидкой 3%")
                .contains("Наблюдений: 35")
                .contains("Регион: Москва")
                .contains("Цена может отличаться");
        assertThat(messageCaptor.getValue().getInlineKeyboard()).isNotEmpty();
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
                .contains("за всё время текущей подписки")
                .contains("пока недостаточно данных")
                .contains("Регион: Москва")
                .contains("Цена может отличаться")
                .doesNotContain("Последняя известная цена")
                .doesNotContain("Минимум")
                .doesNotContain("Максимум")
                .doesNotContain("Средняя")
                .doesNotContain("Наблюдений:");
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
                        RubleAmount.ofMinorUnits(43_400L),
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
                .contains("Последняя известная цена — минимальная за выбранный период.")
                .doesNotContain("выше минимума");
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
