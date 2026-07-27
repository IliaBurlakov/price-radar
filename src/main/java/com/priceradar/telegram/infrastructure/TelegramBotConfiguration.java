package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.telegram.application.LatestSnapshotMessageFactory;
import com.priceradar.telegram.application.ShowLastKnownCallbackHandler;
import com.priceradar.telegram.application.StatisticsCallbackHandler;
import com.priceradar.telegram.application.StatisticsMessageFactory;
import com.priceradar.telegram.application.TelegramCurrentQuoteHandler;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.telegram.application.TargetPriceParser;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramQuoteMessageFactory;
import com.priceradar.telegram.application.TelegramTrackingHandler;
import com.priceradar.telegram.application.TrackingCallbackCodec;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import com.priceradar.telegram.application.TrackedItemsMessageFactory;
import com.priceradar.telegram.application.TrackedItemsMessageHandler;
import com.priceradar.tracking.application.LatestSnapshotQueryService;
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
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new TelegramBotApiClient(
                httpClient,
                objectMapper,
                properties.getApiBaseUrl(),
                botToken,
                properties.getRequestTimeout(),
                properties.getMaxRetryAfter(),
                properties.getMaxResponseBytes()
        );
    }

    @Bean
    public TrackingCallbackCodec trackingCallbackCodec(
            @Value("${TELEGRAM_CALLBACK_SECRET}") String callbackSecret
    ) {
        return new TrackingCallbackCodec(callbackSecret);
    }

    @Bean
    public TelegramQuoteMessageFactory telegramQuoteMessageFactory(
            TrackingCallbackCodec trackingCallbackCodec
    ) {
        return new TelegramQuoteMessageFactory(
                new WalletEstimateService(),
                trackingCallbackCodec
        );
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
    public TelegramTrackingHandler telegramTrackingHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TargetPriceParser targetPriceParser,
            TrackingCallbackCodec trackingCallbackCodec,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TelegramTrackingHandler(
                userProfileService,
                subscriptionService,
                targetPriceParser,
                trackingCallbackCodec,
                pendingTargetPriceStore,
                telegramGateway,
                providerClock
        );
    }

    @Bean
    public TrackedItemsMessageFactory trackedItemsMessageFactory() {
        return new TrackedItemsMessageFactory();
    }

    @Bean
    public TrackedItemsMessageHandler trackedItemsMessageHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TrackedItemsMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TrackedItemsMessageHandler(
                userProfileService,
                subscriptionService,
                messageFactory,
                telegramGateway,
                providerClock
        );
    }

    @Bean
    public LatestSnapshotMessageFactory latestSnapshotMessageFactory() {
        return new LatestSnapshotMessageFactory(new WalletEstimateService());
    }

    @Bean
    public ShowLastKnownCallbackHandler showLastKnownCallbackHandler(
            UserProfileService userProfileService,
            LatestSnapshotQueryService queryService,
            LatestSnapshotMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        return new ShowLastKnownCallbackHandler(
                userProfileService,
                queryService,
                messageFactory,
                telegramGateway
        );
    }

    @Bean
    public StatisticsMessageFactory statisticsMessageFactory() {
        return new StatisticsMessageFactory();
    }

    @Bean
    public StatisticsCallbackHandler statisticsCallbackHandler(
            UserProfileService userProfileService,
            SubscriptionStatisticsService statisticsService,
            StatisticsMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new StatisticsCallbackHandler(
                userProfileService,
                statisticsService,
                messageFactory,
                telegramGateway,
                providerClock
        );
    }

    @Bean
    public TelegramUpdateDispatcher telegramUpdateDispatcher(
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramTrackingHandler trackingHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler,
            StatisticsCallbackHandler statisticsHandler
    ) {
        return new TelegramUpdateDispatcher(
                currentQuoteHandler,
                trackingHandler,
                trackedItemsHandler,
                showLastKnownHandler,
                statisticsHandler
        );
    }

    @Bean
    public TelegramLongPollingWorker telegramLongPollingWorker(
            TelegramGateway telegramGateway,
            TelegramUpdateDispatcher updateDispatcher,
            TelegramPollingStateStore pollingStateStore,
            TelegramBotProperties properties,
            Clock providerClock
    ) {
        return new TelegramLongPollingWorker(
                telegramGateway,
                updateDispatcher,
                pollingStateStore,
                properties.getBotKey(),
                properties.getLongPollingTimeout(),
                properties.getMaxUpdateAttempts(),
                providerClock
        );
    }
}
