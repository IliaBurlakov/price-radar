package com.priceradar.telegram.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public final class TelegramTutorialHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramTutorialHandler.class);

    private final TutorialAssetCatalog assetCatalog;
    private final TelegramTutorialMessageFactory messageFactory;
    private final TelegramGateway telegramGateway;
    private final boolean enabled;

    public TelegramTutorialHandler(
            TutorialAssetCatalog assetCatalog,
            TelegramTutorialMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        this(assetCatalog, messageFactory, telegramGateway, true);
    }

    public TelegramTutorialHandler(
            TutorialAssetCatalog assetCatalog,
            TelegramTutorialMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            boolean enabled
    ) {
        if (assetCatalog == null || messageFactory == null || telegramGateway == null) {
            throw new IllegalArgumentException("tutorial handler dependencies must not be null");
        }
        this.assetCatalog = assetCatalog;
        this.messageFactory = messageFactory;
        this.telegramGateway = telegramGateway;
        this.enabled = enabled;
    }

    public boolean handleCallback(IncomingTelegramCallback callback) {
        Optional<TutorialCallbackData> parsed = TutorialCallbackData.parse(callback.getData());
        if (parsed.isEmpty()) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        if (!callback.isPrivateChat()) {
            return true;
        }
        TutorialCallbackData data = parsed.orElseThrow();
        if (data.getAction() == TutorialCallbackData.Action.OPEN) {
            telegramGateway.sendMessage(messageFactory.platformChooser(
                    callback.getChatId(), data.getTopic()
            ));
            return true;
        }
        showTutorial(callback.getChatId(), data.getTopic(), data.getPlatform().orElseThrow());
        return true;
    }

    private void showTutorial(
            long chatId,
            TutorialTopic topic,
            TutorialPlatform platform
    ) {
        List<TutorialAsset> assets;
        try {
            assets = assetCatalog.load(topic, platform);
        } catch (TutorialAssetException exception) {
            LOGGER.error("Tutorial resource is unavailable, resourcePath={}",
                    exception.getResourcePath(), exception);
            sendFailureBestEffort(chatId,
                    "⚠️ Сейчас не удалось открыть инструкцию. Попробуйте ещё раз немного позже.");
            return;
        }

        try {
            telegramGateway.sendMessage(messageFactory.loading(chatId));
            List<OutgoingTelegramMediaGroup.Photo> photos = assets.stream()
                    .map(asset -> new OutgoingTelegramMediaGroup.Photo(
                            asset.getFilename(), asset.getContent()
                    ))
                    .toList();
            telegramGateway.sendMediaGroup(new OutgoingTelegramMediaGroup(
                    chatId,
                    messageFactory.instructionText(topic, platform),
                    photos
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn("Tutorial delivery was incomplete, topic={}, platform={}, errorType={}",
                    topic, platform, exception.getClass().getSimpleName(), exception);
            sendFailureBestEffort(chatId,
                    "⚠️ Не удалось полностью загрузить инструкцию. Попробуйте открыть её ещё раз.");
            return;
        }
        sendNavigationBestEffort(chatId, topic);
    }

    private void sendNavigationBestEffort(long chatId, TutorialTopic topic) {
        try {
            telegramGateway.sendMessage(messageFactory.navigation(chatId, topic));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not send tutorial navigation, topic={}, errorType={}",
                    topic, exception.getClass().getSimpleName());
        }
    }

    private void sendFailureBestEffort(long chatId, String text) {
        try {
            telegramGateway.sendMessage(OutgoingTelegramMessage.text(chatId, text));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not send tutorial failure message, errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
