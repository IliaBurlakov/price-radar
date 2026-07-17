package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.telegram.application.TelegramCurrentQuoteHandler;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.telegram.application.TargetPriceParser;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramQuoteMessageFactory;
import com.priceradar.telegram.application.TelegramTrackingHandler;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.user.application.UserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Clock;

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
    public TargetPriceParser targetPriceParser() {
        return new TargetPriceParser();
    }

    @Bean
    public PendingTargetPriceStore pendingTargetPriceStore() {
        return new PendingTargetPriceStore();
    }

    @Bean
    public TelegramTrackingHandler telegramTrackingHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TargetPriceParser targetPriceParser,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TelegramTrackingHandler(
                userProfileService,
                subscriptionService,
                targetPriceParser,
                pendingTargetPriceStore,
                telegramGateway,
                providerClock
        );
    }

    @Bean
    public TelegramUpdateDispatcher telegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramTrackingHandler trackingHandler
    ) {
        return new TelegramUpdateDispatcher(currentQuoteHandler, trackingHandler);
    }

    @Bean
    public TelegramLongPollingWorker telegramLongPollingWorker(
            TelegramGateway telegramGateway,
            TelegramUpdateDispatcher updateDispatcher,
            TelegramPollingStateStore pollingStateStore,
            TelegramBotProperties properties
    ) {
        return new TelegramLongPollingWorker(
                telegramGateway,
                updateDispatcher,
                pollingStateStore,
                properties.getBotKey(),
                properties.getLongPollingTimeout()
        );
    }
}
