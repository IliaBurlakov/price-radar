package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.telegram.application.TelegramBotCommand;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TelegramBotApiClientTest {

    @Test
    void registersTheGlobalCommandMenuThroughTelegramApi() throws Exception {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/bot123:secret/setMyCommands", 200, "{\"ok\":true,\"result\":true}");
            ObjectMapper objectMapper = new ObjectMapper();
            TelegramBotApiClient client = client(stub, objectMapper);

            client.registerCommands(List.of(TelegramBotCommand.values()));

            assertThat(stub.requestCount()).isOne();
            assertThat(objectMapper.readTree(stub.lastRequestBody()).path("commands"))
                    .extracting(node -> node.path("command").textValue())
                    .containsExactly("start", "tracked", "add", "import", "help");
            assertThat(objectMapper.readTree(stub.lastRequestBody()).path("commands"))
                    .extracting(node -> node.path("description").textValue())
                    .containsExactly(
                            "🏠 Главное меню",
                            "📦 Мои товары",
                            "➕ Добавить товар",
                            "🛒 Импортировать корзину",
                            "❓ Помощь"
                    );
        }
    }

    @Test
    void startupRegistrationRunsOnceAndTelegramFailureDoesNotStopStartup() throws Exception {
        TelegramBotCommandRegistrar registrar = mock(TelegramBotCommandRegistrar.class);
        TelegramCommandMenuInitializer initializer = new TelegramCommandMenuInitializer(registrar);

        initializer.run(mock(ApplicationArguments.class));

        verify(registrar, times(1)).registerCommands(List.of(TelegramBotCommand.values()));

        TelegramBotCommandRegistrar unavailable = mock(TelegramBotCommandRegistrar.class);
        doThrow(new TelegramDeliveryException("temporary outage", true))
                .when(unavailable).registerCommands(List.of(TelegramBotCommand.values()));

        assertThatCode(() -> new TelegramCommandMenuInitializer(unavailable)
                .run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();

        TelegramBotCommandRegistrar invalidConfiguration = mock(TelegramBotCommandRegistrar.class);
        doThrow(new TelegramDeliveryException("invalid bot token"))
                .when(invalidConfiguration).registerCommands(List.of(TelegramBotCommand.values()));

        assertThatThrownBy(() -> new TelegramCommandMenuInitializer(invalidConfiguration)
                .run(mock(ApplicationArguments.class)))
                .isInstanceOf(TelegramDeliveryException.class);
    }

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

    private TelegramBotApiClient client(LocalHttpStub stub, ObjectMapper objectMapper) {
        return new TelegramBotApiClient(
                HttpClient.newHttpClient(),
                objectMapper,
                stub.baseUri(),
                "123:secret",
                Duration.ofSeconds(2),
                Duration.ofHours(1),
                1024 * 1024
        );
    }
}
