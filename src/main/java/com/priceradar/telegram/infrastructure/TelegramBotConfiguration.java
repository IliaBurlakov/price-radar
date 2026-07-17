package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.telegram.application.TelegramCurrentQuoteHandler;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramQuoteMessageFactory;
import com.priceradar.user.application.UserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TelegramBotProperties.class)
@ConditionalOnProperty(prefix = "priceradar.telegram", name = "enabled", havingValue = "true")
public class TelegramBotConfiguration {

    @Bean
    public TelegramGateway telegramGateway(
            ObjectMapper objectMapper,
            TelegramBotProperties properties,
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new TelegramBotApiClient(
                httpClient,
                objectMapper,
                properties.getApiBaseUrl(),
                botToken,
                properties.getRequestTimeout()
        );
    }

    @Bean
    public TelegramQuoteMessageFactory telegramQuoteMessageFactory() {
        return new TelegramQuoteMessageFactory(new WalletEstimateService());
    }

    @Bean
    public TelegramCurrentQuoteHandler telegramCurrentQuoteHandler(
            UserProfileService userProfileService,
            ResolvedQuoteService resolvedQuoteService,
            TelegramQuoteMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        return new TelegramCurrentQuoteHandler(
                userProfileService,
                resolvedQuoteService,
                messageFactory,
                telegramGateway
        );
    }

    @Bean
    public TelegramLongPollingWorker telegramLongPollingWorker(
            TelegramGateway telegramGateway,
            TelegramCurrentQuoteHandler updateHandler,
            TelegramPollingStateStore pollingStateStore,
            TelegramBotProperties properties
    ) {
        return new TelegramLongPollingWorker(
                telegramGateway,
                updateHandler,
                pollingStateStore,
                properties.getBotKey(),
                properties.getLongPollingTimeout()
        );
    }
}
