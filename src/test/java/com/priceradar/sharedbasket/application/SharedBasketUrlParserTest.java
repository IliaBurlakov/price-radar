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
        assertThat(parser.parse("https://wildberries.ru/basket?shareId=abc123def4"))
                .isEqualTo("abc123def4");
        assertThat(parser.supports("https://www.wildberries.ru/basket?shareId=abc123def4"))
                .isTrue();
        assertThat(parser.supports("https://wildberries.ru/basket?shareId=abc123def4"))
                .isTrue();

        assertThatThrownBy(() -> parser.parse("http://www.wildberries.ru/basket?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("http://wildberries.ru/basket?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://evilwildberries.ru/basket?shareId=abc123def4"))
                .isInstanceOf(InvalidSharedBasketUrlException.class);
        assertThatThrownBy(() -> parser.parse("https://wildberries.ru.evil.com/basket?shareId=abc123def4"))
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
