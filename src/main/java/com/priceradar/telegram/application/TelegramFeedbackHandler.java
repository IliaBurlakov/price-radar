package com.priceradar.telegram.application;

import com.priceradar.feedback.application.FeedbackService;
import com.priceradar.feedback.application.FeedbackSubmissionResult;
import com.priceradar.feedback.application.PendingFeedbackInput;
import com.priceradar.feedback.domain.FeedbackMessage;
import com.priceradar.feedback.infrastructure.configuration.FeedbackProperties;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;

public final class TelegramFeedbackHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramFeedbackHandler.class);

    private final UserProfileService userProfileService;
    private final FeedbackService feedbackService;
    private final TelegramGateway telegramGateway;
    private final FeedbackProperties properties;
    private final Clock clock;

    public TelegramFeedbackHandler(UserProfileService userProfileService,
                                   FeedbackService feedbackService,
                                   TelegramGateway telegramGateway,
                                   FeedbackProperties properties,
                                   Clock clock) {
        if (userProfileService == null || feedbackService == null || telegramGateway == null
                || properties == null || clock == null) {
            throw new IllegalArgumentException("feedback handler dependencies must not be null");
        }
        this.userProfileService = userProfileService;
        this.feedbackService = feedbackService;
        this.telegramGateway = telegramGateway;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean supportsCallback(IncomingTelegramCallback callback) {
        return MainMenuCallbackData.parse(callback.getData())
                .filter(action -> action == MainMenuCallbackData.Action.FEEDBACK)
                .isPresent();
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        if (!supportsCallback(callback)) return false;
        if (callback.isPrivateChat()) {
            show(callback.getTelegramUserId(), callback.getChatId());
        }
        return true;
    }

    public void show(long telegramUserId, long chatId) {
        UserProfile profile = userProfileService.getOrCreate(
                telegramUserId, chatId
        );
        feedbackService.begin(profile);
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                chatId,
                "💬 Обратная связь\n\nСпасибо, что пользуетесь Price Radar! Здесь вы можете оставить свой отзыв о сервисе и помочь нам стать лучше.\n\nОтправьте сообщение в течение %d минут."
                        .formatted(feedbackService.getPendingTtl().toMinutes()),
                TelegramNavigationKeyboard.home()
        ));
    }

    public boolean handlePendingMessage(IncomingTelegramMessage message) {
        if (message == null || !message.isPrivateChat()) return false;
        if (TelegramBotCommand.isCommandText(message.getText())) {
            feedbackService.cancel(message.getTelegramUserId(), message.getChatId());
            return false;
        }
        Optional<PendingFeedbackInput> found = feedbackService.findPending(
                message.getTelegramUserId(), message.getChatId()
        );
        if (found.isEmpty()) return false;
        if (found.orElseThrow().isExpired(clock.instant())) {
            feedbackService.cancel(message.getTelegramUserId(), message.getChatId());
            telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(),
                    "Время отправки обратной связи истекло. Откройте форму ещё раз через главное меню.",
                    TelegramNavigationKeyboard.mainMenu()
            ));
            return true;
        }
        UserProfile profile = userProfileService.getOrCreate(
                message.getTelegramUserId(), message.getChatId()
        );
        FeedbackSubmissionResult result = feedbackService.submit(profile, message.getText());
        switch (result.getStatus()) {
            case BLANK -> sendValidation(message.getChatId(), "Сообщение не должно быть пустым.");
            case TOO_LONG -> sendValidation(message.getChatId(),
                    "Сообщение слишком длинное. Сократите его до %d символов."
                            .formatted(feedbackService.getMaxLength()));
            case EXPIRED -> telegramGateway.sendMessage(new OutgoingTelegramMessage(
                    message.getChatId(), "Время отправки обратной связи истекло. Откройте форму ещё раз.",
                    TelegramNavigationKeyboard.mainMenu()));
            case SUBMITTED -> confirmAndNotify(profile, result.getFeedback().orElseThrow());
            case NOT_PENDING -> { return false; }
        }
        return true;
    }

    public void cancel(long telegramUserId, long chatId) {
        feedbackService.cancel(telegramUserId, chatId);
    }

    private void sendValidation(long chatId, String text) {
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                chatId, text + " Попробуйте ещё раз.", TelegramNavigationKeyboard.home()
        ));
    }

    private void confirmAndNotify(UserProfile profile, FeedbackMessage feedback) {
        telegramGateway.sendMessage(new OutgoingTelegramMessage(
                profile.getTelegramChatId(),
                "✅ Спасибо за обратную связь!\n\nСообщение получено. Ваш отзыв поможет сделать Price Radar лучше.",
                TelegramNavigationKeyboard.mainMenu()
        ));
        if (properties.getRecipientChatId() == 0) {
            LOGGER.warn("Feedback delivery is disabled, feedbackId={}", feedback.getId());
            return;
        }
        try {
            String city = profile.getLocation().map(location -> location.getSettlementName())
                    .orElse("не выбран");
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(
                    properties.getRecipientChatId(),
                    "💬 Новая обратная связь\n\nFeedback ID: %s\nTelegram user ID: %d\nГород: %s\n\n%s"
                            .formatted(feedback.getId(), profile.getTelegramUserId(), city,
                                    feedback.getMessage())
            ));
        } catch (RuntimeException exception) {
            String failureType = exception instanceof TelegramDeliveryException deliveryException
                    ? deliveryException.getFailureType().name()
                    : exception.getClass().getSimpleName();
            LOGGER.warn("Could not deliver feedback copy, feedbackId={}, failureType={}",
                    feedback.getId(), failureType);
        }
    }
}
