package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.telegram.application.OutgoingTelegramMediaGroup;
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
    void sendsThreePngFilesAsOneMultipartMediaGroupWithCaption() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/bot123:secret/sendMediaGroup", 200,
                    "{\"ok\":true,\"result\":[{\"message_id\":1}]}");
            TelegramBotApiClient client = client(stub, new ObjectMapper());

            client.sendMediaGroup(mediaGroup());

            assertThat(stub.lastRequestContentType()).startsWith("multipart/form-data; boundary=");
            String requestBody = stub.lastRequestBody();
            assertThat(requestBody)
                    .contains("name=\"chat_id\"", "7001", "name=\"media\"",
                            "attach://photo0", "attach://photo1", "attach://photo2",
                            "Инструкция",
                            "name=\"photo0\"", "name=\"photo1\"", "name=\"photo2\"",
                            "filename=\"step-1.png\"", "filename=\"step-2.png\"",
                            "filename=\"step-3.png\"", "Content-Type: image/png");
            assertThat(requestBody).doesNotContain("show_caption_above_media");
            assertThat(countOccurrences(requestBody, "caption")).isOne();
        }
    }

    @Test
    void appliesExistingTelegramFailureSemanticsToMediaGroupDelivery() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/bot123:secret/sendMediaGroup", 400,
                    "{\"ok\":false,\"error_code\":400,"
                            + "\"description\":\"Bad Request: invalid media payload\"}");
            TelegramBotApiClient client = client(stub, new ObjectMapper());

            assertThatThrownBy(() -> client.sendMediaGroup(mediaGroup()))
                    .isInstanceOfSatisfying(TelegramDeliveryException.class, exception ->
                            {
                                assertThat(exception.getFailureType())
                                        .isEqualTo(TelegramDeliveryFailureType.PERMANENT_FAILURE);
                                assertThat(exception.getMessage())
                                        .contains("Bad Request: invalid media payload");
                            });
        }

        HttpClient unavailable = mock(HttpClient.class);
        try {
            when(unavailable.send(
                    any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()
            )).thenThrow(new ConnectException("connection refused"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertThatThrownBy(() -> client(unavailable).sendMediaGroup(mediaGroup()))
                .isInstanceOfSatisfying(TelegramDeliveryException.class, exception ->
                assertThat(exception.getFailureType())
                        .isEqualTo(TelegramDeliveryFailureType.SAFE_TO_RETRY));

        HttpClient timedOut = mock(HttpClient.class);
        try {
            when(timedOut.send(
                    any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()
            )).thenThrow(new java.net.http.HttpTimeoutException("response timeout"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertThatThrownBy(() -> client(timedOut).sendMediaGroup(mediaGroup()))
                .isInstanceOfSatisfying(TelegramDeliveryException.class, exception ->
                assertThat(exception.getFailureType())
                        .isEqualTo(TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS));
    }

    @Test
    void rejectsTelegramOkFalseForMediaGroupEvenWithHttpSuccess() {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/bot123:secret/sendMediaGroup", 200,
                    "{\"ok\":false,\"error_code\":500,"
                            + "\"description\":\"Internal media processing error\"}");
            TelegramBotApiClient client = client(stub, new ObjectMapper());

            assertThatThrownBy(() -> client.sendMediaGroup(mediaGroup()))
                    .isInstanceOfSatisfying(TelegramDeliveryException.class, exception ->
                            {
                                assertThat(exception.getFailureType())
                                        .isEqualTo(TelegramDeliveryFailureType.SAFE_TO_RETRY);
                                assertThat(exception.getMessage())
                                        .contains("Internal media processing error");
                            });
        }
    }

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
                    .containsExactly(
                            "start", "add", "import", "tracked", "city", "wallet", "help", "feedback"
                    );
            assertThat(objectMapper.readTree(stub.lastRequestBody()).path("commands"))
                    .extracting(node -> node.path("description").textValue())
                    .containsExactly(
                            "🏠 Главное меню",
                            "➕ Добавить товар",
                            "🛒 Импортировать корзину",
                            "📦 Мои товары",
                            "🌍 Город",
                            "💳 WB Кошелёк",
                            "❓ Помощь",
                            "💬 Обратная связь"
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

    private OutgoingTelegramMediaGroup mediaGroup() {
        return new OutgoingTelegramMediaGroup(
                7001L,
                "Инструкция",
                List.of(
                        new OutgoingTelegramMediaGroup.Photo("step-1.png", new byte[]{1}),
                        new OutgoingTelegramMediaGroup.Photo("step-2.png", new byte[]{2}),
                        new OutgoingTelegramMediaGroup.Photo("step-3.png", new byte[]{3})
                )
        );
    }

    private int countOccurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
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
