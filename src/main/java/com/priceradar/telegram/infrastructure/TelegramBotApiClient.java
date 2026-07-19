package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.telegram.application.IncomingTelegramMessage;
import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramInlineButton;
import com.priceradar.telegram.application.TelegramUpdate;

import java.io.IOException;
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

    public TelegramBotApiClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI apiBaseUrl,
            String botToken,
            Duration requestTimeout
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
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = ensureTrailingSlash(apiBaseUrl);
        this.botToken = botToken;
        this.requestTimeout = requestTimeout;
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
        requestBody.putArray("allowed_updates").add("message");

        JsonNode result = call(
                "getUpdates",
                requestBody,
                timeout.plus(requestTimeout)
        );
        List<TelegramUpdate> updates = new ArrayList<>();
        for (JsonNode updateNode : result) {
            if (!updateNode.path("update_id").canConvertToLong()) {
                continue;
            }
            long updateId = updateNode.path("update_id").longValue();
            updates.add(new TelegramUpdate(updateId, parseMessage(updateNode.path("message"))));
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

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramGatewayException("Telegram request was interrupted");
        } catch (IOException exception) {
            throw new TelegramGatewayException("Telegram API is temporarily unavailable");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TelegramGatewayException(
                    "Telegram API returned HTTP " + response.statusCode()
            );
        }

        JsonNode responseBody = readJson(response.body());
        if (!responseBody.path("ok").asBoolean(false) || !responseBody.path("result").isContainerNode()) {
            throw new TelegramGatewayException("Telegram API returned an unsuccessful response");
        }
        return responseBody.path("result");
    }

    private String writeJson(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new TelegramGatewayException("Could not create Telegram request");
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TelegramGatewayException("Telegram API returned malformed JSON");
        }
    }

    private URI methodUri(String method) {
        return apiBaseUrl.resolve("bot" + botToken + "/" + method);
    }

    private URI ensureTrailingSlash(URI uri) {
        String value = uri.toString();
        return value.endsWith("/") ? uri : URI.create(value + "/");
    }
}
