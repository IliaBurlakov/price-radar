package com.priceradar.telegram.application;

import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class WildberriesLinkExtractorTest {

    private final WildberriesLinkExtractor extractor = new WildberriesLinkExtractor(
            new ProductUrlParser(), new SharedBasketUrlParser(), 50
    );

    @Test
    void extractsOfficialBasketAndProductLinksWithoutWww() {
        WildberriesLinks basket = extractor.extract(
                "https://wildberries.ru/basket?shareId=mqy5s5pm4z"
        );
        WildberriesLinks product = extractor.extract(
                "https://wildberries.ru/catalog/10302974/detail.aspx"
        );

        assertThat(basket.getSharedBasketUrl())
                .contains("https://wildberries.ru/basket?shareId=mqy5s5pm4z");
        assertThat(product.getProductLinks()).singleElement()
                .satisfies(link -> assertThat(link.getParsedUrl().getNmId())
                        .isEqualTo(10_302_974L));
    }

    @Test
    void extractsProductFromCleanUrlAndSurroundingTextAndRemovesTrailingPunctuation() {
        String url = "https://www.wildberries.ru/catalog/389025161/detail.aspx?size=564351571";

        for (String input : new String[]{url, "SHEPOT Candles\n" + url,
                url + "\nочень нравится", "Посмотри\n" + url + ",\nцена была около 2500",
                "(" + url + ").", "\"" + url + "\"", "«" + url + "»",
                url + "!", url + ",цена=2500", "[" + url + "]"}) {
            WildberriesLinks result = extractor.extract(input);

            assertThat(result.getProductLinks()).singleElement().satisfies(link -> {
                assertThat(link.getUrl()).isEqualTo(url);
                assertThat(link.getParsedUrl().getNmId()).isEqualTo(389025161L);
                assertThat(link.getParsedUrl().getRequestedSizeId()).hasValue(564351571L);
            });
        }
    }

    @Test
    void preservesOrderAndDeduplicatesByProductAndExplicitSize() {
        String first = "https://www.wildberries.ru/catalog/111/detail.aspx?size=10";
        String sameProductAnotherSize = "https://www.wildberries.ru/catalog/111/detail.aspx?size=11";
        String second = "https://www.wildberries.ru/catalog/222/detail.aspx";

        WildberriesLinks result = extractor.extract(
                first + "\n" + second + "\n" + first + "\n" + sameProductAnotherSize
        );

        assertThat(result.getProductLinks()).extracting(link -> link.getParsedUrl().getNmId())
                .containsExactly(111L, 222L, 111L);
        assertThat(result.getProductLinks().get(0).getParsedUrl().getRequestedSizeId()).hasValue(10L);
        assertThat(result.getProductLinks().get(2).getParsedUrl().getRequestedSizeId()).hasValue(11L);
    }

    @Test
    void basketInsideTextWinsOverProductsAndFirstDistinctBasketIsUsed() {
        String firstBasket = "https://www.wildberries.ru/basket?shareId=abcde12345";
        String secondBasket = "https://www.wildberries.ru/basket?shareId=fghij67890";

        WildberriesLinks result = extractor.extract("Товар: https://www.wildberries.ru/catalog/111/detail.aspx\n"
                + "Моя корзина: " + firstBasket + ",\n" + firstBasket + "\n" + secondBasket);

        assertThat(result.getSharedBasketUrl()).contains(firstBasket);
        assertThat(result.getProductLinks()).isEmpty();
    }

    @Test
    void ignoresGarbageAndExternalUrlsWithoutRejectingValidProducts() {
        WildberriesLinks result = extractor.extract("текст https://example.com/foo\n"
                + "https://www.wildberries.ru/catalog/not-a-number/detail.aspx\n"
                + "https://www.wildberries.ru/catalog/111/detail.aspx\nещё текст");

        assertThat(result.getProductLinks()).singleElement()
                .satisfies(link -> assertThat(link.getParsedUrl().getNmId()).isEqualTo(111L));
        assertThat(result.getInvalidWildberriesUrlCount()).isEqualTo(1);
        assertThat(extractor.extract("только обычный текст https://example.com").getProductLinks()).isEmpty();
    }

    @Test
    void capsUniqueProductsAtFiftyAndMarksResultAsTruncated() {
        String input = IntStream.rangeClosed(1, 51)
                .mapToObj(id -> "https://www.wildberries.ru/catalog/" + id + "/detail.aspx")
                .collect(Collectors.joining("\n"));

        WildberriesLinks result = extractor.extract(input);

        assertThat(result.getProductLinks()).hasSize(50);
        assertThat(result.getProductLinks().getFirst().getParsedUrl().getNmId()).isEqualTo(1L);
        assertThat(result.getProductLinks().getLast().getParsedUrl().getNmId()).isEqualTo(50L);
        assertThat(result.isTruncated()).isTrue();
    }

    @Test
    void extractsAllEightLinksFromRealWorldListWithoutReadingSurroundingData() {
        String input = """
                Илюша одежда
                1. Базовые футболки - Fanur Футболка хлопок набор 5 шт большие размеры https://www.wildberries.ru/catalog/569886969/detail.aspx?size=781052231 2580
                2. Тонкий кардиган 2-3 шт
                1) МАТОСТИЛЬ Свитер базовый c круглым воротником
                https://www.wildberries.ru/catalog/698772746/detail.aspx 2785,
                2) МАТОСТИЛЬ Свитер базовый с круглым воротником
                https://www.wildberries.ru/catalog/702273121/detail.aspx?size=976394743 2785
                1. Рубашки 2 шт -
                1) Chilandge Рубашка в полоску https://www.wildberries.ru/catalog/254986606/detail.aspx?size=397601472 2788,
                2) MAILLIV TREND Премиум рубашка в полоску оверсайз с длинным рукавом хлопок https://www.wildberries.ru/catalog/436683519/detail.aspx 3154
                2. Джинсы прямого кроя темные Feimailis Джинсы широкие трубы y2k багги https://www.wildberries.ru/catalog/829829020/detail.aspx?size=1245948922 2849
                3. Брюки светлые - Диагональ Широкие оверсайз-брюки из костюмной ткани https://www.wildberries.ru/catalog/437022878/detail.aspx?size=619917779 3764
                4. Диагональ Брюки классические широкие оверсайз https://www.wildberries.ru/catalog/608447882/detail.aspx?size=827349531 3686
                """;

        WildberriesLinks result = extractor.extract(input);

        assertThat(result.getProductLinks()).hasSize(8);
        assertThat(result.getProductLinks()).extracting(link -> link.getParsedUrl().getNmId())
                .containsExactly(569886969L, 698772746L, 702273121L, 254986606L,
                        436683519L, 829829020L, 437022878L, 608447882L);
        assertThat(result.getProductLinks()).extracting(link ->
                        link.getParsedUrl().getRequestedSizeId().isPresent()
                                ? link.getParsedUrl().getRequestedSizeId().getAsLong() : null)
                .containsExactly(781052231L, null, 976394743L, 397601472L,
                        null, 1245948922L, 619917779L, 827349531L);
    }
}
