package com.priceradar.telegram.application;

import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedItemsMessageFactoryTest {

    private final TrackedItemsMessageFactory factory =
            new TrackedItemsMessageFactory(new WalletEstimateService());

    @Test
    void createsPagedProductListWithOneButtonPerProduct() {
        List<TrackedSubscriptionItem> items = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            items.add(item("Товар " + index, 1000L + index, Optional.empty()));
        }

        OutgoingTelegramMessage firstPage = factory.createList(7001L, items, 0, "Москва");
        OutgoingTelegramMessage secondPage = factory.createList(7001L, items, 1, "Москва");

        assertThat(firstPage.getText())
                .contains("📦 Мои товары")
                .contains("Отслеживаемые товары для города Москва.")
                .contains("Всего товаров: 9")
                .contains("Страница 1 из 2")
                .doesNotContain("1. Товар 1", "Выберите товар:");
        assertThat(firstPage.getInlineKeyboard())
                .filteredOn(row -> row.size() == 1
                        && row.getFirst().getCallbackData().startsWith("TRACKED_ITEM:"))
                .extracting(row -> row.getFirst().getText())
                .containsExactly(
                        "1. Товар 1", "2. Товар 2", "3. Товар 3", "4. Товар 4",
                        "5. Товар 5", "6. Товар 6", "7. Товар 7", "8. Товар 8"
                );
        assertThat(secondPage.getInlineKeyboard())
                .filteredOn(row -> row.size() == 1
                        && row.getFirst().getCallbackData().startsWith("TRACKED_ITEM:"))
                .singleElement()
                .satisfies(row -> assertThat(row.getFirst().getText())
                        .isEqualTo("9. Товар 9"));
    }

    @Test
    void showsBothPricesAndHidesProviderNoSizePlaceholderInDetails() {
        TrackedSubscriptionItem item = item(
                "Сумерки",
                122_200L,
                Optional.of("Size: 0")
        );

        OutgoingTelegramMessage message = factory.createDetails(
                7001L,
                item,
                1,
                0,
                "Moscow",
                UserPricePreferences.defaults()
        );

        assertThat(message.getText())
                .contains("Цена без WB Кошелька: 1 222 ₽")
                .contains("С WB Кошельком: ≈ 1 185 ₽")
                .doesNotContain("Size: 0")
                .doesNotContain("Размер: 0");
        assertThat(message.getInlineKeyboard())
                .flatExtracting(row -> row)
                .extracting(TelegramInlineButton::getText)
                .contains(
                        "📊 Статистика",
                        "🔔 Условие уведомлений",
                        "❌ Удалить товар",
                        "← Назад"
                )
                .doesNotContain("💰 Последняя цена");
    }

    private TrackedSubscriptionItem item(
            String title,
            long regularPriceMinor,
            Optional<String> variantDisplayName
    ) {
        Instant observedAt = Instant.parse("2026-07-27T16:35:00Z");
        return new TrackedSubscriptionItem(
                UUID.randomUUID(),
                Math.max(1L, regularPriceMinor),
                Optional.of(title),
                Optional.of("Бренд"),
                "https://www.wildberries.ru/catalog/123456/detail.aspx",
                variantDisplayName,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                observedAt.minusSeconds(60),
                Optional.of(SnapshotStatus.REGULAR_PRICE),
                Optional.of(RubleAmount.ofMinorUnits(regularPriceMinor)),
                Optional.of(observedAt)
        );
    }
}
