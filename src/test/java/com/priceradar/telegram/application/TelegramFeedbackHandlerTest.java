package com.priceradar.telegram.application;

import com.priceradar.feedback.application.FeedbackService;
import com.priceradar.feedback.application.FeedbackSubmissionResult;
import com.priceradar.feedback.application.PendingFeedbackInput;
import com.priceradar.feedback.domain.FeedbackMessage;
import com.priceradar.feedback.infrastructure.configuration.FeedbackProperties;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramFeedbackHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");

    @Test
    void opensWithoutCityAndSubmitsOrdinaryTextBeforeOnboarding() {
        UserProfileService users = mock(UserProfileService.class);
        FeedbackService service = mock(FeedbackService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        UserProfile profile = profile();
        when(users.getOrCreate(7001L, 7001L)).thenReturn(profile);
        FeedbackProperties properties = new FeedbackProperties();
        properties.setRecipientChatId(9001L);
        TelegramFeedbackHandler handler = handler(users, service, gateway, properties);
        when(service.getPendingTtl()).thenReturn(Duration.ofMinutes(15));
        IncomingTelegramCallback callback = new IncomingTelegramCallback(
                "cb", 7001L, 7001L, "private",
                MainMenuCallbackData.encode(MainMenuCallbackData.Action.FEEDBACK));

        assertThat(handler.handleCallback(callback)).isTrue();
        verify(service).begin(profile);

        PendingFeedbackInput pending = new PendingFeedbackInput(profile.getId(), 7001L, 7001L,
                NOW, NOW.plusSeconds(900));
        FeedbackMessage feedback = new FeedbackMessage(UUID.randomUUID(), profile.getId(),
                "https://www.wildberries.ru/catalog/1/detail.aspx", NOW);
        when(service.findPending(7001L, 7001L)).thenReturn(Optional.of(pending));
        when(service.submit(profile, feedback.getMessage()))
                .thenReturn(FeedbackSubmissionResult.submitted(feedback));

        assertThat(handler.handlePendingMessage(message(feedback.getMessage()))).isTrue();

        ArgumentCaptor<OutgoingTelegramMessage> sent = ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway, org.mockito.Mockito.times(3)).sendMessage(sent.capture());
        assertThat(sent.getAllValues().get(1).getText()).contains("Спасибо");
        assertThat(sent.getAllValues().get(2).getText())
                .contains(feedback.getId().toString(), "Telegram user ID: 7001", "Город: не выбран", feedback.getMessage());
    }

    @Test
    void commandCancelsPendingAndContinuesNormalRouting() {
        UserProfileService users = mock(UserProfileService.class);
        FeedbackService service = mock(FeedbackService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        FeedbackProperties properties = new FeedbackProperties();
        properties.setRecipientChatId(9001L);
        TelegramFeedbackHandler handler = handler(users, service, gateway, properties);

        assertThat(handler.handlePendingMessage(message("/help"))).isFalse();
        verify(service).cancel(7001L, 7001L);
        verify(service, never()).submit(any(), any());
    }

    @Test
    void developerDeliveryFailureHappensAfterUserConfirmationAndDoesNotResubmit() {
        UserProfileService users = mock(UserProfileService.class);
        FeedbackService service = mock(FeedbackService.class);
        TelegramGateway gateway = mock(TelegramGateway.class);
        FeedbackProperties properties = new FeedbackProperties();
        properties.setRecipientChatId(9001L);
        UserProfile profile = profile();
        PendingFeedbackInput pending = new PendingFeedbackInput(profile.getId(), 7001L, 7001L,
                NOW, NOW.plusSeconds(900));
        FeedbackMessage feedback = new FeedbackMessage(UUID.randomUUID(), profile.getId(), "Ошибка", NOW);
        when(users.getOrCreate(7001L, 7001L)).thenReturn(profile);
        when(service.findPending(7001L, 7001L)).thenReturn(Optional.of(pending));
        when(service.submit(profile, "Ошибка")).thenReturn(FeedbackSubmissionResult.submitted(feedback));
        org.mockito.Mockito.doAnswer(invocation -> {
            OutgoingTelegramMessage outgoing = invocation.getArgument(0);
            if (outgoing.getChatId() == 9001L) throw new IllegalStateException("network");
            return null;
        }).when(gateway).sendMessage(any());
        TelegramFeedbackHandler handler = handler(users, service, gateway, properties);

        assertThat(handler.handlePendingMessage(message("Ошибка"))).isTrue();

        verify(service).submit(profile, "Ошибка");
        ArgumentCaptor<OutgoingTelegramMessage> sent = ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway, org.mockito.Mockito.times(2)).sendMessage(sent.capture());
        assertThat(sent.getAllValues().getFirst().getText()).contains("Спасибо");
    }

    private TelegramFeedbackHandler handler(UserProfileService users, FeedbackService service,
                                            TelegramGateway gateway, FeedbackProperties properties) {
        return new TelegramFeedbackHandler(users, service, gateway, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile profile() {
        return new UserProfile(UUID.randomUUID(), 7001L, 7001L, null,
                UserPricePreferences.defaults());
    }

    private IncomingTelegramMessage message(String text) {
        return new IncomingTelegramMessage(7001L, 7001L, "private", text);
    }
}
