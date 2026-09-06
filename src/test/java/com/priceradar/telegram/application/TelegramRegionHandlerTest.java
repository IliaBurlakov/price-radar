package com.priceradar.telegram.application;

import com.priceradar.region.application.CitySelectionResult;
import com.priceradar.region.application.CitySelectionService;
import com.priceradar.region.application.PendingCitySelection;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramRegionHandlerTest {

    private static final long TELEGRAM_ID = 7001L;
    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Test
    void screenRequestsCityTextWithoutExposingProviderContext() {
        Context context = context(profile(moscow(), true));

        context.handler.show(TELEGRAM_ID, TELEGRAM_ID);

        OutgoingTelegramMessage message = captured(context.gateway);
        assertThat(message.getText())
                .contains(
                        "Выберите город",
                        "Введите город",
                        "Сейчас выбран: Москва"
                )
                .doesNotContain("1259570991", "dest", "spp");
        verify(context.selection).begin(context.profile, NOW);
    }

    @Test
    void oneResultIsConfirmedAndShowsMainNavigation() {
        UserProfile profile = profile(null, false);
        Context context = context(profile);
        ResolvedLocation selected = moscow();
        when(context.selection.hasPending(profile, NOW)).thenReturn(true);
        when(context.selection.pending(profile, NOW)).thenReturn(Optional.of(
                new PendingCitySelection(UUID.randomUUID(), profile.getId(), List.of(),
                        NOW.minusSeconds(1), NOW.plusSeconds(60))
        ));
        when(context.selection.search(profile, "Москва", NOW)).thenReturn(
                CitySelectionResult.selected(CitySelectionResult.Status.SELECTED, selected)
        );

        assertThat(context.handler.handleMessage(message("Москва"))).isTrue();

        var messages = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().getFirst().getText()).contains("Ищу город");
        assertThat(messages.getAllValues().getLast().getText()).contains("Город выбран: Москва");
        assertThat(messages.getAllValues().getLast().getInlineKeyboard()).isNotEmpty();
    }

    @Test
    void ambiguousResultUsesShortLabelsAndInvalidNumberKeepsPrompt() {
        UserProfile profile = profile(null, false);
        Context context = context(profile);
        GeoCandidate first = candidate("Киров", "Кировская область", null, 58.60, 49.66);
        GeoCandidate second = candidate("Киров", "Калужская область", null, 54.08, 34.30);
        PendingCitySelection pending = new PendingCitySelection(
                UUID.randomUUID(), profile.getId(), List.of(first, second),
                NOW.minusSeconds(1), NOW.plusSeconds(60)
        );
        when(context.selection.hasPending(profile, NOW)).thenReturn(true);
        when(context.selection.pending(profile, NOW)).thenReturn(Optional.of(pending));
        context.handler.handleMessage(message("7"));

        assertThat(captured(context.gateway).getText()).isEqualTo("Введите номер от 1 до 2.");
        verify(context.selection, never()).choose(profile, 7, NOW);
    }

    @Test
    void validOptionShowsImmediateFeedbackBeforeResolvingSelection() {
        UserProfile profile = profile(null, false);
        Context context = context(profile);
        GeoCandidate first = candidate("Киров", "Кировская область", null, 58.60, 49.66);
        GeoCandidate second = candidate("Киров", "Калужская область", null, 54.08, 34.30);
        when(context.selection.hasPending(profile, NOW)).thenReturn(true);
        when(context.selection.pending(profile, NOW)).thenReturn(Optional.of(
                new PendingCitySelection(UUID.randomUUID(), profile.getId(), List.of(first, second),
                        NOW.minusSeconds(1), NOW.plusSeconds(60))
        ));
        when(context.selection.choose(profile, 2, NOW)).thenReturn(
                CitySelectionResult.selected(CitySelectionResult.Status.SELECTED, moscow())
        );

        context.handler.handleMessage(message("2"));

        var order = inOrder(context.gateway, context.selection);
        var progress = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        order.verify(context.gateway).sendMessage(progress.capture());
        assertThat(progress.getValue().getText())
                .isEqualTo("⏳ Устанавливаю город...");
        order.verify(context.selection).choose(profile, 2, NOW);
        var messages = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().getLast().getText()).contains("Город выбран: Москва");
    }

    @Test
    void newTextWhileOptionsArePendingStartsFreshSearch() {
        UserProfile profile = profile(null, false);
        Context context = context(profile);
        when(context.selection.hasPending(profile, NOW)).thenReturn(true);
        when(context.selection.pending(profile, NOW)).thenReturn(Optional.of(
                new PendingCitySelection(UUID.randomUUID(), profile.getId(),
                        List.of(candidate("Киров", "Кировская область", null, 58.60, 49.66)),
                        NOW.minusSeconds(1), NOW.plusSeconds(60))
        ));
        when(context.selection.search(profile, "Томск", NOW)).thenReturn(
                CitySelectionResult.status(CitySelectionResult.Status.NOT_FOUND)
        );

        context.handler.handleMessage(message("Томск"));

        verify(context.selection).search(profile, "Томск", NOW);
        var messages = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(context.gateway, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().getFirst().getText())
                .isEqualTo("🔎 Ищу город...");
        assertThat(messages.getAllValues())
                .extracting(OutgoingTelegramMessage::getText)
                .noneMatch(text -> text.contains("Устанавливаю город"));
    }

    @Test
    void wildberriesUrlIsNotHandledAsCityAfterPendingSelectionWasCancelled() {
        UserProfile profile = profile(moscow(), true);
        Context context = context(profile);
        when(context.selection.hasPending(profile, NOW)).thenReturn(false);
        IncomingTelegramMessage productUrl = message(
                "https://www.wildberries.ru/catalog/123/detail.aspx"
        );

        assertThat(context.handler.handleMessage(productUrl)).isFalse();

        verify(context.selection, never()).search(profile, productUrl.getText(), NOW);
    }

    private Context context(UserProfile profile) {
        UserProfileService users = mock(UserProfileService.class);
        CitySelectionService selection = mock(CitySelectionService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        when(users.getOrCreate(TELEGRAM_ID, TELEGRAM_ID)).thenReturn(profile);
        return new Context(
                profile, selection, gateway,
                new TelegramRegionHandler(
                        users, selection, gateway, Clock.fixed(NOW, ZoneOffset.UTC),
                        new GeoLocationLabelFormatter()
                )
        );
    }

    private UserProfile profile(ResolvedLocation location, boolean selected) {
        return new UserProfile(
                UUID.randomUUID(), TELEGRAM_ID, TELEGRAM_ID,
                selected ? location : null, UserPricePreferences.defaults()
        );
    }

    private GeoCandidate candidate(
            String name, String region, String district, double latitude, double longitude
    ) {
        return new GeoCandidate(
                name, region, district, "Россия", BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude), "relation", UUID.randomUUID().toString(),
                "place", "city", 0.8, 16
        );
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(TELEGRAM_ID, TELEGRAM_ID, "private", text);
    }

    private OutgoingTelegramMessage captured(TelegramGateway gateway) {
        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        return message.getValue();
    }

    private static final class Context {
        private final UserProfile profile;
        private final CitySelectionService selection;
        private final TelegramGateway gateway;
        private final TelegramRegionHandler handler;

        private Context(
                UserProfile profile,
                CitySelectionService selection,
                TelegramGateway gateway,
                TelegramRegionHandler handler
        ) {
            this.profile = profile;
            this.selection = selection;
            this.gateway = gateway;
            this.handler = handler;
        }
    }
}
