package com.priceradar.telegram.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramTutorialHandlerTest {

    @Test
    void disabledTutorialsAreHiddenAndOldCallbacksCannotOpenThem() {
        TelegramMenuMessageFactory menuFactory = new TelegramMenuMessageFactory(50, false);
        assertThat(menuFactory.addProduct(1L).getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Главное меню");
        assertThat(menuFactory.importBasket(1L).getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .containsExactly("Главное меню");

        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramTutorialHandler handler = new TelegramTutorialHandler(
                new TutorialAssetCatalog(), new TelegramTutorialMessageFactory(), gateway, false
        );

        assertThat(handler.handleCallback(callback(TutorialCallbackData.open(
                TutorialTopic.PRODUCT
        )))).isTrue();
        verifyNoInteractions(gateway);
    }

    @Test
    void chooserContainsOnlySupportedPlatformsAndReturnsToItsSourceScreen() {
        TelegramTutorialMessageFactory factory = new TelegramTutorialMessageFactory();

        assertChooser(factory.platformChooser(1L, TutorialTopic.PRODUCT),
                MainMenuCallbackData.Action.ADD_PRODUCT);
        assertChooser(factory.platformChooser(1L, TutorialTopic.BASKET),
                MainMenuCallbackData.Action.IMPORT_BASKET);
    }

    @Test
    void missingResourceShowsOnlyAUserFriendlyMessage() {
        ClassLoader emptyClassLoader = new ClassLoader(null) {
        };
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramTutorialHandler handler = new TelegramTutorialHandler(
                new TutorialAssetCatalog(emptyClassLoader),
                new TelegramTutorialMessageFactory(),
                gateway
        );

        handler.handleCallback(callback(TutorialCallbackData.show(
                TutorialTopic.BASKET, TutorialPlatform.WINDOWS
        )));

        var message = org.mockito.ArgumentCaptor.forClass(OutgoingTelegramMessage.class);
        verify(gateway).sendMessage(message.capture());
        assertThat(message.getValue().getText())
                .isEqualTo("⚠️ Сейчас не удалось открыть инструкцию. Попробуйте ещё раз немного позже.")
                .doesNotContain("telegram/tutorial", "Exception");
        verify(gateway, org.mockito.Mockito.never()).sendMediaGroup(any());
    }

    private void assertChooser(
            OutgoingTelegramMessage message,
            MainMenuCallbackData.Action expectedBack
    ) {
        List<TelegramInlineButton> buttons = message.getInlineKeyboard().stream()
                .flatMap(List::stream)
                .toList();
        assertThat(buttons).extracting(TelegramInlineButton::getText)
                .containsExactly(
                        "🍎 iPhone", "🤖 Android", "💻 Компьютер (Windows)",
                        "← Назад", "Главное меню"
                );
        assertThat(buttons.get(3).getCallbackData())
                .isEqualTo(MainMenuCallbackData.encode(expectedBack));
        assertThat(buttons.getLast().getCallbackData())
                .isEqualTo(MainMenuCallbackData.encode(MainMenuCallbackData.Action.HOME));
    }

    private IncomingTelegramCallback callback(String data) {
        return new IncomingTelegramCallback("callback", 1L, 1L, "private", data);
    }
}
