package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.telegram.application.IncomingTelegramCallback;
import com.priceradar.telegram.application.IncomingTelegramMessage;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.OutgoingTelegramMediaGroup;
import com.priceradar.telegram.application.TelegramBotCommand;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramDeliveryFailureType;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramInlineButton;
import com.priceradar.telegram.application.TelegramUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TelegramBotApiClient implements TelegramGateway, TelegramBotCommandRegistrar {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiBaseUrl;
    private final String botToken;
    private final Duration requestTimeout;
    private final Duration maxRetryAfter;
    private final int maxResponseBytes;

    public TelegramBotApiClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI apiBaseUrl,
            String botToken,
            Duration requestTimeout,
            Duration maxRetryAfter,
            int maxResponseBytes
    ) {
        if (httpClient == null || objectMapper == null || apiBaseUrl == null || requestTimeout == null) {
            throw new IllegalArgumentException("Telegram API client dependencies must not be null");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN must not be blank");
        }
        if (botToken.contains("/") || botToken.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN has invalid characters");
        }
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Telegram request timeout must be positive");
        }
        validateApiBaseUrl(apiBaseUrl);
        if (maxRetryAfter == null || maxRetryAfter.compareTo(Duration.ofSeconds(1)) < 0
                || maxRetryAfter.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException(
                    "Telegram max retry delay must be between 1 second and 24 hours"
            );
        }
        if (maxResponseBytes <= 0 || maxResponseBytes > 4 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Telegram response size limit must be between 1 and 4194304"
            );
        }
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = ensureTrailingSlash(apiBaseUrl);
        this.botToken = botToken;
        this.requestTimeout = requestTimeout;
        this.maxRetryAfter = maxRetryAfter;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public List<TelegramUpdate> receiveUpdates(long offset, Duration timeout) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("long polling timeout must be positive");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("offset", offset);
        requestBody.put("timeout", Math.toIntExact(timeout.toSeconds()));
        requestBody.putArray("allowed_updates")
                .add("message")
                .add("callback_query");

        JsonNode result = call(
                "getUpdates",
                requestBody,
                timeout.plus(requestTimeout)
        );
        if (!result.isArray()) {
            throw permanentFailure("Telegram API returned invalid updates data");
        }
        List<TelegramUpdate> updates = new ArrayList<>();
        for (JsonNode updateNode : result) {
            if (!updateNode.path("update_id").canConvertToLong()) {
                continue;
            }
            long updateId = updateNode.path("update_id").longValue();
            updates.add(new TelegramUpdate(
                    updateId,
                    parseMessage(updateNode.path("message")),
                    parseCallback(updateNode.path("callback_query"))
            ));
        }
        updates.sort(Comparator.comparingLong(TelegramUpdate::getUpdateId));
        return List.copyOf(updates);
    }

    @Override
    public void sendMessage(OutgoingTelegramMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("chat_id", message.getChatId());
        requestBody.put("text", message.getText());
        if (!message.getInlineKeyboard().isEmpty()) {
            requestBody.set("reply_markup", createReplyMarkup(message.getInlineKeyboard()));
        }
        call("sendMessage", requestBody, requestTimeout);
    }

    @Override
    public void sendMediaGroup(OutgoingTelegramMediaGroup mediaGroup) {
        if (mediaGroup == null) {
            throw new IllegalArgumentException("mediaGroup must not be null");
        }
        String boundary = "PriceRadar-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(methodUri("sendMediaGroup"))
                .timeout(requestTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartMediaGroupBody(mediaGroup, boundary))
                .build();
        execute(request);
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException("callbackQueryId must not be blank");
        }
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("callback_query_id", callbackQueryId.trim());
        call("answerCallbackQuery", requestBody, requestTimeout);
    }

    @Override
    public void registerCommands(List<TelegramBotCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("commands must not be null, empty or contain nulls");
        }
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode commandNodes = requestBody.putArray("commands");
        for (TelegramBotCommand command : commands) {
            ObjectNode commandNode = commandNodes.addObject();
            commandNode.put("command", command.getCommand());
            commandNode.put("description", command.getDescription());
        }
        call("setMyCommands", requestBody, requestTimeout);
    }

    private Optional<IncomingTelegramMessage> parseMessage(JsonNode messageNode) {
        JsonNode fromNode = messageNode.path("from");
        JsonNode chatNode = messageNode.path("chat");
        JsonNode textNode = messageNode.path("text");
        if (!fromNode.path("id").canConvertToLong()
                || !chatNode.path("id").canConvertToLong()
                || !chatNode.path("type").isTextual()
                || !textNode.isTextual()) {
            return Optional.empty();
        }

        long telegramUserId = fromNode.path("id").longValue();
        long chatId = chatNode.path("id").longValue();
        if (telegramUserId <= 0 || chatId <= 0) {
            return Optional.empty();
        }
        return Optional.of(new IncomingTelegramMessage(
                telegramUserId,
                chatId,
                chatNode.path("type").textValue(),
                textNode.textValue()
        ));
    }

    private Optional<IncomingTelegramCallback> parseCallback(JsonNode callbackNode) {
        JsonNode fromNode = callbackNode.path("from");
        JsonNode chatNode = callbackNode.path("message").path("chat");
        JsonNode idNode = callbackNode.path("id");
        JsonNode dataNode = callbackNode.path("data");
        if (!idNode.isTextual()
                || !fromNode.path("id").canConvertToLong()
                || !chatNode.path("id").canConvertToLong()
                || !chatNode.path("type").isTextual()
                || !dataNode.isTextual()) {
            return Optional.empty();
        }

        long telegramUserId = fromNode.path("id").longValue();
        long chatId = chatNode.path("id").longValue();
        if (telegramUserId <= 0 || chatId <= 0) {
            return Optional.empty();
        }
        return Optional.of(new IncomingTelegramCallback(
                idNode.textValue(),
                telegramUserId,
                chatId,
                chatNode.path("type").textValue(),
                dataNode.textValue()
        ));
    }

    private ObjectNode createReplyMarkup(List<List<TelegramInlineButton>> keyboard) {
        ObjectNode replyMarkup = objectMapper.createObjectNode();
        ArrayNode inlineKeyboard = replyMarkup.putArray("inline_keyboard");
        for (List<TelegramInlineButton> row : keyboard) {
            ArrayNode rowNode = inlineKeyboard.addArray();
            for (TelegramInlineButton button : row) {
                ObjectNode buttonNode = rowNode.addObject();
                buttonNode.put("text", button.getText());
                buttonNode.put("callback_data", button.getCallbackData());
            }
        }
        return replyMarkup;
    }

    private JsonNode call(String method, JsonNode requestBody, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(methodUri(method))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(requestBody)))
                .build();

        return execute(request);
    }

    private JsonNode execute(HttpRequest request) {

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramDeliveryException(
                    "Telegram request was interrupted before its outcome was confirmed",
                    TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS,
                    exception
            );
        } catch (IOException exception) {
            throw transportFailure(exception);
        }

        String rawResponse = readBoundedBody(response.body(), response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Optional<Duration> retryAfter = response.statusCode() == 429
                    ? extractRetryAfter(rawResponse)
                    : Optional.empty();
            throw new TelegramDeliveryException(
                    telegramErrorMessage(
                            "Telegram API returned HTTP " + response.statusCode(),
                            rawResponse
                    ),
                    response.statusCode() == 429 || response.statusCode() >= 500
                            ? TelegramDeliveryFailureType.SAFE_TO_RETRY
                            : TelegramDeliveryFailureType.PERMANENT_FAILURE,
                    retryAfter,
                    null
            );
        }

        JsonNode responseBody = readSuccessfulResponseJson(rawResponse);
        if (responseBody == null) {
            throw new TelegramDeliveryException(
                    "Telegram accepted the request but returned an empty response",
                    TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS
            );
        }
        JsonNode result = responseBody.path("result");
        if (!responseBody.path("ok").asBoolean(false)) {
            int errorCode = responseBody.path("error_code").asInt(0);
            Optional<Duration> retryAfter = errorCode == 429
                    ? extractRetryAfter(rawResponse)
                    : Optional.empty();
            throw new TelegramDeliveryException(
                    telegramErrorMessage(
                            errorCode > 0
                                    ? "Telegram API returned error " + errorCode
                                    : "Telegram API returned an unsuccessful response",
                            rawResponse
                    ),
                    errorCode == 429 || errorCode >= 500
                            ? TelegramDeliveryFailureType.SAFE_TO_RETRY
                            : TelegramDeliveryFailureType.PERMANENT_FAILURE,
                    retryAfter,
                    null
            );
        }
        if (result.isMissingNode() || result.isNull()) {
            throw new TelegramDeliveryException(
                    "Telegram accepted the request but omitted the result",
                    TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS
            );
        }
        return result;
    }

    private HttpRequest.BodyPublisher multipartMediaGroupBody(
            OutgoingTelegramMediaGroup mediaGroup,
            String boundary
    ) {
        ArrayNode media = objectMapper.createArrayNode();
        for (int index = 0; index < mediaGroup.getPhotos().size(); index++) {
            ObjectNode item = media.addObject();
            item.put("type", "photo");
            item.put("media", "attach://photo" + index);
            if (index == 0) {
                item.put("caption", mediaGroup.getCaption());
            }
        }

        List<byte[]> parts = new ArrayList<>();
        parts.add(textMultipartPart(boundary, "chat_id", Long.toString(mediaGroup.getChatId())));
        parts.add(textMultipartPart(boundary, "media", writeJson(media)));
        for (int index = 0; index < mediaGroup.getPhotos().size(); index++) {
            OutgoingTelegramMediaGroup.Photo photo = mediaGroup.getPhotos().get(index);
            parts.add(utf8("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"photo" + index
                    + "\"; filename=\"" + photo.getFilename() + "\"\r\n"
                    + "Content-Type: image/png\r\n\r\n"));
            parts.add(photo.getContent());
            parts.add(utf8("\r\n"));
        }
        parts.add(utf8("--" + boundary + "--\r\n"));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private byte[] textMultipartPart(String boundary, String name, String value) {
        return utf8("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String writeJson(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new TelegramDeliveryException(
                    "Could not create Telegram request",
                    TelegramDeliveryFailureType.PERMANENT_FAILURE,
                    exception
            );
        }
    }

    private JsonNode readSuccessfulResponseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TelegramDeliveryException(
                    "Telegram accepted the request but returned malformed JSON",
                    TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS,
                    exception
            );
        }
    }

    private String readBoundedBody(InputStream body, int statusCode) {
        boolean successfulHttpStatus = statusCode >= 200 && statusCode < 300;
        try (body) {
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new TelegramDeliveryException(
                        "Telegram API response is too large",
                        successfulHttpStatus
                                ? TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS
                                : failureTypeForHttpError(statusCode)
                );
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new TelegramDeliveryException(
                    "Could not read Telegram API response",
                    successfulHttpStatus
                            ? TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS
                            : failureTypeForHttpError(statusCode),
                    exception
            );
        }
    }

    private TelegramDeliveryFailureType failureTypeForHttpError(int statusCode) {
        return statusCode == 429 || statusCode >= 500
                ? TelegramDeliveryFailureType.SAFE_TO_RETRY
                : TelegramDeliveryFailureType.PERMANENT_FAILURE;
    }

    private TelegramDeliveryException transportFailure(IOException exception) {
        TelegramDeliveryFailureType failureType = isDefinitelyNotSent(exception)
                ? TelegramDeliveryFailureType.SAFE_TO_RETRY
                : TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS;
        String message = failureType == TelegramDeliveryFailureType.SAFE_TO_RETRY
                ? "Telegram API connection could not be established"
                : "Telegram request outcome could not be confirmed";
        return new TelegramDeliveryException(message, failureType, exception);
    }

    private boolean isDefinitelyNotSent(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof HttpConnectTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private TelegramDeliveryException permanentFailure(String message) {
        return new TelegramDeliveryException(
                message,
                TelegramDeliveryFailureType.PERMANENT_FAILURE
        );
    }

    private Optional<Duration> extractRetryAfter(String rawResponse) {
        try {
            JsonNode response = objectMapper.readTree(rawResponse);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode value = response
                    .path("parameters")
                    .path("retry_after");
            if (!value.canConvertToLong() || value.longValue() <= 0) {
                return Optional.empty();
            }
            long cappedSeconds = Math.min(value.longValue(), maxRetryAfter.toSeconds());
            return Optional.of(Duration.ofSeconds(cappedSeconds));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String telegramErrorMessage(String fallback, String rawResponse) {
        try {
            JsonNode response = objectMapper.readTree(rawResponse);
            if (response == null || !response.path("description").isTextual()) {
                return fallback;
            }
            String description = response.path("description").textValue()
                    .replaceAll("[\\r\\n\\t]+", " ")
                    .trim();
            if (description.isBlank()) {
                return fallback;
            }
            int end = description.offsetByCodePoints(
                    0,
                    Math.min(300, description.codePointCount(0, description.length()))
            );
            return fallback + ": " + description.substring(0, end);
        } catch (IOException exception) {
            return fallback;
        }
    }

    private URI methodUri(String method) {
        return apiBaseUrl.resolve("./bot" + botToken + "/" + method);
    }

    private URI ensureTrailingSlash(URI uri) {
        String value = uri.toString();
        return value.endsWith("/") ? uri : URI.create(value + "/");
    }

    private void validateApiBaseUrl(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopbackHttp = "http".equalsIgnoreCase(scheme)
                && host != null
                && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]"));
        boolean trustedHost = "api.telegram.org".equalsIgnoreCase(host) || loopbackHttp;
        String path = uri.getPath();
        boolean validPath = path == null || path.isEmpty() || path.equals("/");
        if (!uri.isAbsolute() || host == null || host.isBlank()
                || (!("https".equalsIgnoreCase(scheme)) && !loopbackHttp)
                || !trustedHost
                || !validPath
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Telegram apiBaseUrl must be an absolute HTTPS URI without user info, query or fragment"
            );
        }
    }
}
