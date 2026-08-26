package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.telegram.application.TelegramBotCommand;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramDeliveryFailureType;
import com.priceradar.testsupport.LocalHttpStub;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.net.http.HttpClient;
import java.net.ConnectException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        doThrow(new TelegramDeliveryException(
                "temporary outage",
                TelegramDeliveryFailureType.SAFE_TO_RETRY
        ))
                .when(unavailable).registerCommands(List.of(TelegramBotCommand.values()));

        assertThatCode(() -> new TelegramCommandMenuInitializer(unavailable)
                .run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();

        TelegramBotCommandRegistrar invalidConfiguration = mock(TelegramBotCommandRegistrar.class);
        doThrow(new TelegramDeliveryException(
                "invalid bot token",
                TelegramDeliveryFailureType.PERMANENT_FAILURE
        ))
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
                        assertThat(exception.getFailureType())
                                .isEqualTo(TelegramDeliveryFailureType.SAFE_TO_RETRY);
                        assertThat(exception.getRetryAfter()).contains(Duration.ofHours(1));
                    });
        }
    }

    @Test
    void classifiesConnectionFailureBeforeExchangeAsSafeToRetry() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenThrow(new ConnectException("connection refused"));
        TelegramBotApiClient client = client(httpClient);

        assertThatThrownBy(() -> client.sendMessage(
                com.priceradar.telegram.application.OutgoingTelegramMessage.text(1L, "test")
        )).isInstanceOfSatisfying(TelegramDeliveryException.class, exception -> {
            assertThat(exception.getFailureType())
                    .isEqualTo(TelegramDeliveryFailureType.SAFE_TO_RETRY);
            assertThat(exception.getCause()).isInstanceOf(ConnectException.class);
        });
    }

    @Test
    void classifiesUnconfirmedResponseAsAmbiguous() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenThrow(new java.net.http.HttpTimeoutException("response timeout"));
        TelegramBotApiClient client = client(httpClient);

        assertThatThrownBy(() -> client.sendMessage(
                com.priceradar.telegram.application.OutgoingTelegramMessage.text(1L, "test")
        )).isInstanceOfSatisfying(TelegramDeliveryException.class, exception -> {
            assertThat(exception.getFailureType())
                    .isEqualTo(TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS);
            assertThat(exception.getCause())
                    .isInstanceOf(java.net.http.HttpTimeoutException.class);
        });
    }

    @Test
    void classifiesNormalTelegramClientErrorAsPermanent() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub(
                    "/bot123:secret/sendMessage",
                    400,
                    "{\"ok\":false,\"error_code\":400}"
            );
            TelegramBotApiClient client = client(stub, new ObjectMapper());

            assertThatThrownBy(() -> client.sendMessage(
                    com.priceradar.telegram.application.OutgoingTelegramMessage.text(1L, "test")
            )).isInstanceOfSatisfying(TelegramDeliveryException.class, exception ->
                    assertThat(exception.getFailureType())
                            .isEqualTo(TelegramDeliveryFailureType.PERMANENT_FAILURE));
        }
    }

    private TelegramBotApiClient client(LocalHttpStub stub, ObjectMapper objectMapper) {
        return client(HttpClient.newHttpClient(), stub.baseUri(), objectMapper);
    }

    private TelegramBotApiClient client(HttpClient httpClient) {
        return client(httpClient, java.net.URI.create("https://api.telegram.org"), new ObjectMapper());
    }

    private TelegramBotApiClient client(
            HttpClient httpClient,
            java.net.URI apiBaseUrl,
            ObjectMapper objectMapper
    ) {
        return new TelegramBotApiClient(
                httpClient,
                objectMapper,
                apiBaseUrl,
                "123:secret",
                Duration.ofSeconds(2),
                Duration.ofHours(1),
                1024 * 1024
        );
    }
}
