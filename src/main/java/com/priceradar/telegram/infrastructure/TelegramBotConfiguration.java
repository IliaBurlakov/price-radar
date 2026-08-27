package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.telegram.application.LatestSnapshotMessageFactory;
import com.priceradar.telegram.application.ClearTrackingCallbackCodec;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.telegram.application.SharedBasketCallbackCodec;
import com.priceradar.telegram.application.ShowLastKnownCallbackHandler;
import com.priceradar.telegram.application.StatisticsCallbackHandler;
import com.priceradar.telegram.application.StatisticsMessageFactory;
import com.priceradar.telegram.application.TargetPriceParser;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramCurrentQuoteHandler;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramMenuHandler;
import com.priceradar.telegram.application.TelegramMenuMessageFactory;
import com.priceradar.telegram.application.TelegramOnboardingHandler;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramQuoteMessageFactory;
import com.priceradar.telegram.application.TelegramRegionHandler;
import com.priceradar.telegram.application.TelegramSharedBasketHandler;
import com.priceradar.telegram.application.TelegramTrackingHandler;
import com.priceradar.telegram.application.TrackingCallbackCodec;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import com.priceradar.telegram.application.TrackedItemsMessageFactory;
import com.priceradar.telegram.application.TrackedItemsMessageHandler;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import com.priceradar.region.application.UserRegionService;
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
    public TelegramBotApiClient telegramGateway(
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
    public TelegramCommandMenuInitializer telegramCommandMenuInitializer(
            TelegramBotCommandRegistrar commandRegistrar
    ) {
        return new TelegramCommandMenuInitializer(commandRegistrar);
    }

    @Bean
    public SharedBasketCallbackCodec sharedBasketCallbackCodec(
            @Value("${TELEGRAM_CALLBACK_SECRET}") String callbackSecret
    ) {
        return new SharedBasketCallbackCodec(callbackSecret);
    }

    @Bean
    public ClearTrackingCallbackCodec clearTrackingCallbackCodec(
            @Value("${TELEGRAM_CALLBACK_SECRET}") String callbackSecret
    ) {
        return new ClearTrackingCallbackCodec(callbackSecret);
    }

    @Bean
    public TelegramSharedBasketHandler telegramSharedBasketHandler(
            SharedBasketUrlParser urlParser,
            SharedBasketImportService importService,
            SharedBasketCallbackCodec callbackCodec,
            UserProfileService userProfileService,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TelegramSharedBasketHandler(
                urlParser, importService, callbackCodec, userProfileService, telegramGateway, providerClock
        );
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
            ProductUrlParser productUrlParser,
            UserProfileService userProfileService,
            ResolvedQuoteService resolvedQuoteService,
            TelegramQuoteMessageFactory messageFactory,
            TelegramGateway telegramGateway
    ) {
        return new TelegramCurrentQuoteHandler(
                productUrlParser,
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
        return new TrackedItemsMessageFactory(new WalletEstimateService());
    }

    @Bean
    public TrackedItemsMessageHandler trackedItemsMessageHandler(
            UserProfileService userProfileService,
            SubscriptionService subscriptionService,
            TrackedItemsMessageFactory messageFactory,
            ClearTrackingCallbackCodec clearTrackingCallbackCodec,
            PendingTargetPriceStore pendingTargetPriceStore,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TrackedItemsMessageHandler(
                userProfileService,
                subscriptionService,
                messageFactory,
                clearTrackingCallbackCodec,
                pendingTargetPriceStore,
                telegramGateway,
                providerClock
        );
    }

    @Bean
    public TelegramRegionHandler telegramRegionHandler(
            UserProfileService userProfileService,
            UserRegionService userRegionService,
            TelegramGateway telegramGateway,
            Clock providerClock
    ) {
        return new TelegramRegionHandler(
                userProfileService, userRegionService, telegramGateway, providerClock
        );
    }

    @Bean
    public TelegramMenuMessageFactory telegramMenuMessageFactory() {
        return new TelegramMenuMessageFactory();
    }

    @Bean
    public TelegramMenuHandler telegramMenuHandler(
            TelegramMenuMessageFactory messageFactory,
            TrackedItemsMessageHandler trackedItemsHandler,
            TelegramRegionHandler regionHandler,
            TelegramGateway telegramGateway
    ) {
        return new TelegramMenuHandler(
                messageFactory,
                trackedItemsHandler,
                regionHandler,
                telegramGateway
        );
    }

    @Bean
    public TelegramOnboardingHandler telegramOnboardingHandler(
            UserProfileService userProfileService,
            TelegramRegionHandler regionHandler
    ) {
        return new TelegramOnboardingHandler(userProfileService, regionHandler);
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
        return new StatisticsMessageFactory(new WalletEstimateService());
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
            TelegramOnboardingHandler onboardingHandler,
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramMenuHandler menuHandler,
            TelegramTrackingHandler trackingHandler,
            TelegramSharedBasketHandler sharedBasketHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler,
            StatisticsCallbackHandler statisticsHandler,
            TelegramGateway telegramGateway
    ) {
        return new TelegramUpdateDispatcher(
                onboardingHandler,
                currentQuoteHandler,
                menuHandler,
                trackingHandler,
                sharedBasketHandler,
                trackedItemsHandler,
                showLastKnownHandler,
                statisticsHandler,
                telegramGateway
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
