package com.priceradar.telegram.application;

import com.priceradar.region.application.RegionChangeResult;
import com.priceradar.region.application.UserRegionService;
import com.priceradar.region.domain.MarketplaceRegionCode;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import com.priceradar.product.application.ResolvedQuoteResult;
import com.priceradar.product.application.ResolvedQuoteService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.irkutsk;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramRegionHandlerTest {

    private static final long TELEGRAM_ID = 7001L;
    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void regionScreenMarksCurrentCityWithoutExposingProviderDestination() {
        UserProfileService users = mock(UserProfileService.class);
        UserRegionService regions = mock(UserRegionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = profile(moscow());
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(regions.listEnabled()).thenReturn(List.of(moscow(), irkutsk()));
        TelegramRegionHandler handler = handler(users, regions, gateway);

        handler.show(TELEGRAM_ID, TELEGRAM_ID);

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText())
                .contains("Город", "Сейчас выбран: Москва")
                .doesNotContain("1259570991", "dest");
        assertThat(message.getValue().getInlineKeyboard()).flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("✓ Москва", "Иркутск", "← Назад");
    }

    @Test
    void blockedChangeOffersClearAllAndDoesNotHideTheReason() {
        UserProfileService users = mock(UserProfileService.class);
        UserRegionService regions = mock(UserRegionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = profile(moscow());
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(regions.changeRegion(profile.getId(), MarketplaceRegionCode.IRKUTSK, NOW))
                .thenReturn(RegionChangeResult.blocked(irkutsk(), 5));
        TelegramRegionHandler handler = handler(users, regions, gateway);

        handler.handleCallback(new IncomingTelegramCallback(
                "callback", TELEGRAM_ID, TELEGRAM_ID, "private",
                RegionCallbackData.select(MarketplaceRegionCode.IRKUTSK)
        ));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText()).contains(
                "нельзя изменить город", "активные отслеживания: 5",
                "сначала остановите все активные отслеживания"
        );
        assertThat(message.getValue().getInlineKeyboard()).flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("🧹 Очистить все", "Мои товары", "← К городам");
    }

    @Test
    void productQuoteUsesTheUsersCurrentRegionContext() {
        UserProfileService users = mock(UserProfileService.class);
        ResolvedQuoteService quotes = mock(ResolvedQuoteService.class);
        TelegramQuoteMessageFactory messages = mock(TelegramQuoteMessageFactory.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = profile(irkutsk());
        String url = "https://www.wildberries.ru/catalog/123456/detail.aspx";
        ResolvedQuoteResult failure = ResolvedQuoteResult.failure(
                ResolvedQuoteResult.FailureCode.VARIANT_NOT_RESOLVED, "not resolved"
        );
        OutgoingTelegramMessage response = new OutgoingTelegramMessage(
                TELEGRAM_ID, "failure", List.of()
        );
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        when(quotes.resolve(url, irkutsk().toPriceContext())).thenReturn(failure);
        when(messages.createFailureMessage(TELEGRAM_ID, failure)).thenReturn(response);

        new TelegramCurrentQuoteHandler(users, quotes, messages, gateway).handle(
                new IncomingTelegramMessage(TELEGRAM_ID, TELEGRAM_ID, "private", url)
        );

        verify(quotes).resolve(url, irkutsk().toPriceContext());
        verify(gateway).sendMessage(response);
    }

    private TelegramRegionHandler handler(
            UserProfileService users,
            UserRegionService regions,
            TelegramGateway gateway
    ) {
        return new TelegramRegionHandler(
                users, regions, gateway, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private UserProfile profile(com.priceradar.region.domain.MarketplaceRegion region) {
        return new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                region, UserPricePreferences.defaults()
        );
    }
}
