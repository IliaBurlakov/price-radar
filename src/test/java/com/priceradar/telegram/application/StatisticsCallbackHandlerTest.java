package com.priceradar.telegram.application;

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
                new StatisticsMessageFactory(),
                telegramGateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(userProfileService.getOrCreate(TELEGRAM_USER_ID, CHAT_ID))
                .thenReturn(profile);
    }

    @Test
    void rendersSubscriptionStatisticsWithRegionAndApproximatePriceWarning() {
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionStatistics statistics = new SubscriptionStatistics(
                subscriptionId,
                StatisticsPeriod.LAST_30_DAYS,
                NOW.minusSeconds(86_400),
                NOW,
                ObservedPriceStatistics.of(
                        2,
                        RubleAmount.ofMinorUnits(9_000L),
                        RubleAmount.ofMinorUnits(11_001L),
                        new BigDecimal("10000.5")
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
                .contains("Минимальная цена: 90 ₽")
                .contains("Максимальная цена: 110 ₽")
                .contains("Средняя цена: 100 ₽")
                .contains("Количество наблюдений: 2")
                .contains("Регион: Moscow")
                .contains("Цены приблизительные");
        verify(telegramGateway).answerCallbackQuery("statistics-callback");
    }

    @Test
    void reportsNoDataWithoutFabricatedPriceValues() {
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
                .contains("Статистика пока недоступна")
                .contains("Регион: Moscow")
                .contains("Цены приблизительные")
                .doesNotContain("Минимальная цена")
                .doesNotContain("Максимальная цена")
                .doesNotContain("Средняя цена")
                .doesNotContain("Количество наблюдений");
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
