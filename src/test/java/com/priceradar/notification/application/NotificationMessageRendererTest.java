package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageRendererTest {

    private final NotificationMessageRenderer renderer =
            new NotificationMessageRenderer(new WalletEstimateService());

    @Test
    void rendersPriceDecreaseAndReachedTargetMessages() {
        PendingNotificationDelivery priceDecrease = notification(
                NotificationType.PRICE_DECREASE,
                Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(12_000L))
        );
        PendingNotificationDelivery reachedTarget = notification(
                NotificationType.TARGET_REACHED,
                Optional.of(RubleAmount.ofMinorUnits(11_000L)),
                Optional.empty()
        );

        OutgoingTelegramMessage message = renderer.render(priceDecrease);

        assertThat(message.getChatId()).isEqualTo(7001L);
        assertThat(message.getText())
                .contains("Новая минимальная цена")
                .contains("Кофемолка")
                .contains("Предыдущая цена без WB Кошелька: 120 ₽")
                .contains("Предыдущая цена с WB Кошельком: ≈ 116 ₽")
                .contains("Новая цена без WB Кошелька: 100 ₽")
                .contains("Новая цена с WB Кошельком: ≈ 97 ₽")
                .contains("Город: Москва")
                .contains("https://www.wildberries.ru/catalog/123456/detail.aspx")
                .contains("Цена может отличаться");
        assertThat(message.getInlineKeyboard()).isNotEmpty();
        assertThat(renderer.render(reachedTarget).getText())
                .contains("Целевая цена достигнута")
                .contains("Целевая цена: 110 ₽")
                .contains("Новая цена без WB Кошелька: 100 ₽")
                .contains("Новая цена с WB Кошельком: ≈ 97 ₽")
                .contains("Проверено: 18.07.2026 09:00 МСК");
    }

    private PendingNotificationDelivery notification(
            NotificationType type,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> previousPrice
    ) {
        return new PendingNotificationDelivery(
                UUID.randomUUID(),
                type,
                0,
                Instant.parse("2026-07-18T06:00:00Z"),
                true,
                7001L,
                123456L,
                Optional.of("Кофемолка"),
                Optional.of("PriceRadar Test"),
                Optional.of("Белый"),
                "Moscow",
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                3,
                targetPrice,
                previousPrice,
                RubleAmount.ofMinorUnits(10_000L),
                Instant.parse("2026-07-18T06:00:00Z")
        );
    }
}
