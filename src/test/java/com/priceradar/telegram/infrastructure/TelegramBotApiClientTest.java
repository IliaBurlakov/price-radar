package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramBotApiClientTest {

    @Test
    void exposesBoundedTelegramRetryAfter() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/bot123:secret/getUpdates",
                    429,
                    "{\"ok\":false,\"parameters\":{\"retry_after\":7200}}"
            );
            TelegramBotApiClient client = new TelegramBotApiClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    stub.baseUri(),
                    "123:secret",
                    Duration.ofSeconds(2),
                    Duration.ofHours(1),
                    1024 * 1024
            );

            assertThatThrownBy(() -> client.receiveUpdates(0, Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(TelegramDeliveryException.class, exception -> {
                        assertThat(exception.isRetryable()).isTrue();
                        assertThat(exception.getRetryAfter()).contains(Duration.ofHours(1));
                    });
        }
    }
}
