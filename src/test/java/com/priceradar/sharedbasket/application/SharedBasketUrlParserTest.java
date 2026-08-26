package com.priceradar.sharedbasket.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedBasketUrlParserTest {

    private final SharedBasketUrlParser parser = new SharedBasketUrlParser();

    @Test
    void acceptsOnlyTheExactWildberriesSharedBasketShape() {
        assertThat(parser.parse("https://www.wildberries.ru/basket?shareId=abc123def4"))
                .isEqualTo("abc123def4");

        assertThatThrownBy(() -> parser.parse("http://www.wildberries.ru/basket?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://wildberries.ru/basket?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://www.wildberries.ru/catalog?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://www.wildberries.ru/basket"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://www.wildberries.ru/basket?shareId=ABC123"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse(
                "https://www.wildberries.ru/basket?shareId=abc123def4&shareId=abc123def4"
        )).isInstanceOf(InvalidSharedBasketUrlException.class);
    }
}
