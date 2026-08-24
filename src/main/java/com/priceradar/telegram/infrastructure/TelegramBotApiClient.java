package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.telegram.application.IncomingTelegramMessage;
import com.priceradar.telegram.application.IncomingTelegramCallback;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramInlineButton;
import com.priceradar.telegram.application.TelegramUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TelegramBotApiClient implements TelegramGateway {

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
            throw new TelegramDeliveryException("Telegram API returned invalid updates data");
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
    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException("callbackQueryId must not be blank");
        }
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("callback_query_id", callbackQueryId.trim());
        call("answerCallbackQuery", requestBody, requestTimeout);
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

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramDeliveryException("Telegram request was interrupted", true);
        } catch (IOException exception) {
            throw new TelegramDeliveryException("Telegram API is temporarily unavailable", true);
        }

        String rawResponse = readBoundedBody(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Optional<Duration> retryAfter = response.statusCode() == 429
                    ? extractRetryAfter(rawResponse)
                    : Optional.empty();
            throw new TelegramDeliveryException(
                    "Telegram API returned HTTP " + response.statusCode(),
                    response.statusCode() == 429 || response.statusCode() >= 500,
                    retryAfter
            );
        }

        JsonNode responseBody = readJson(rawResponse);
        JsonNode result = responseBody.path("result");
        if (!responseBody.path("ok").asBoolean(false) || result.isMissingNode() || result.isNull()) {
            throw new TelegramDeliveryException("Telegram API returned an unsuccessful response");
        }
        return result;
    }

    private String writeJson(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new TelegramDeliveryException("Could not create Telegram request");
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TelegramDeliveryException("Telegram API returned malformed JSON");
        }
    }

    private String readBoundedBody(InputStream body) {
        try (body) {
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new TelegramDeliveryException("Telegram API response is too large", true);
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new TelegramDeliveryException("Could not read Telegram API response", true);
        }
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
