package com.priceradar.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.configuration.PriceRadarPolicyProperties;
import com.priceradar.feedback.application.FeedbackService;
import com.priceradar.feedback.infrastructure.configuration.FeedbackProperties;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedQuoteBatchService;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.telegram.application.LatestSnapshotMessageFactory;
import com.priceradar.telegram.application.ClearTrackingCallbackCodec;
import com.priceradar.telegram.application.PendingTargetPriceStore;
import com.priceradar.telegram.application.MultiProductQuoteCallbackCodec;
import com.priceradar.telegram.application.MultiProductQuoteSessionStore;
import com.priceradar.telegram.application.SharedBasketCallbackCodec;
import com.priceradar.telegram.application.ShowLastKnownCallbackHandler;
import com.priceradar.telegram.application.StatisticsCallbackHandler;
import com.priceradar.telegram.application.StatisticsMessageFactory;
import com.priceradar.telegram.application.TargetPriceParser;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramCurrentQuoteHandler;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramFeedbackHandler;
import com.priceradar.telegram.application.TelegramMenuHandler;
import com.priceradar.telegram.application.TelegramMenuMessageFactory;
import com.priceradar.telegram.application.TelegramMultiProductQuoteMessageFactory;
import com.priceradar.telegram.application.TelegramOnboardingHandler;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramQuoteMessageFactory;
import com.priceradar.telegram.application.TelegramRegionHandler;
import com.priceradar.telegram.application.TelegramSharedBasketHandler;
import com.priceradar.telegram.application.TelegramTrackingHandler;
import com.priceradar.telegram.application.TelegramTutorialHandler;
import com.priceradar.telegram.application.TelegramTutorialMessageFactory;
import com.priceradar.telegram.application.TutorialAssetCatalog;
import com.priceradar.telegram.application.TrackingCallbackCodec;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import com.priceradar.telegram.application.TrackedItemsMessageFactory;
import com.priceradar.telegram.application.TrackedItemsMessageHandler;
import com.priceradar.telegram.application.WildberriesLinkExtractor;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import com.priceradar.region.application.CitySelectionService;
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
    public WildberriesLinkExtractor wildberriesLinkExtractor(
            ProductUrlParser productUrlParser,
            SharedBasketUrlParser sharedBasketUrlParser,
            PriceRadarPolicyProperties policy
    ) {
        return new WildberriesLinkExtractor(
                productUrlParser, sharedBasketUrlParser, policy.getMaxProductLinksPerMessage()
        );
    }

    @Bean
    public MultiProductQuoteCallbackCodec multiProductQuoteCallbackCodec(
            @Value("${TELEGRAM_CALLBACK_SECRET}") String callbackSecret
    ) {
        return new MultiProductQuoteCallbackCodec(callbackSecret);
    }

    @Bean
    public TelegramMultiProductQuoteMessageFactory telegramMultiProductQuoteMessageFactory(
            MultiProductQuoteCallbackCodec callbackCodec,
            PriceRadarPolicyProperties policy
    ) {
        return new TelegramMultiProductQuoteMessageFactory(
                callbackCodec, policy.getMaxProductLinksPerMessage()
        );
    }

    @Bean
    public TelegramSharedBasketHandler telegramSharedBasketHandler(
            WildberriesLinkExtractor linkExtractor,
            SharedBasketImportService importService,
            SharedBasketCallbackCodec callbackCodec,
            UserProfileService userProfileService,
            TelegramGateway telegramGateway,
            Clock providerClock,
            PriceRadarPolicyProperties policy
    ) {
        return new TelegramSharedBasketHandler(
                linkExtractor, importService, callbackCodec, userProfileService, telegramGateway, providerClock,
                policy.getActiveSubscriptionLimit()
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
            WildberriesLinkExtractor linkExtractor,
            UserProfileService userProfileService,
            ResolvedQuoteService resolvedQuoteService,
            ResolvedQuoteBatchService batchService,
            TelegramQuoteMessageFactory messageFactory,
            TelegramMultiProductQuoteMessageFactory multiProductMessageFactory,
            MultiProductQuoteCallbackCodec multiProductCallbackCodec,
            MultiProductQuoteSessionStore sessionStore,
            SubscriptionService subscriptionService,
            TelegramGateway telegramGateway,
            Clock providerClock,
            PriceRadarPolicyProperties policy
    ) {
        return new TelegramCurrentQuoteHandler(
                linkExtractor,
                userProfileService,
                resolvedQuoteService,
                batchService,
                messageFactory,
                multiProductMessageFactory,
                multiProductCallbackCodec,
                sessionStore,
                subscriptionService,
                telegramGateway,
                providerClock,
                policy.getPendingActionTtl(),
                policy.getMaxProductLinksPerMessage()
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
            Clock providerClock,
            PriceRadarPolicyProperties policy
    ) {
        return new TelegramTrackingHandler(
                userProfileService,
                subscriptionService,
                targetPriceParser,
                trackingCallbackCodec,
                pendingTargetPriceStore,
                telegramGateway,
                providerClock,
                policy.getPendingActionTtl(),
                policy.getActiveSubscriptionLimit()
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
            Clock providerClock,
            PriceRadarPolicyProperties policy
    ) {
        return new TrackedItemsMessageHandler(
                userProfileService,
                subscriptionService,
                messageFactory,
                clearTrackingCallbackCodec,
                pendingTargetPriceStore,
                telegramGateway,
                providerClock,
                policy.getPendingActionTtl()
        );
    }

    @Bean
    public TelegramRegionHandler telegramRegionHandler(
            UserProfileService userProfileService,
            CitySelectionService citySelectionService,
            TelegramGateway telegramGateway,
            Clock providerClock,
            com.priceradar.region.application.GeoLocationLabelFormatter locationLabelFormatter
    ) {
        return new TelegramRegionHandler(
                userProfileService, citySelectionService, telegramGateway, providerClock,
                locationLabelFormatter
        );
    }

    @Bean
    public TelegramMenuMessageFactory telegramMenuMessageFactory(
            PriceRadarPolicyProperties policy,
            TelegramBotProperties telegramProperties
    ) {
        return new TelegramMenuMessageFactory(
                policy.getActiveSubscriptionLimit(),
                telegramProperties.isTutorialsEnabled()
        );
    }

    @Bean
    public TelegramMenuHandler telegramMenuHandler(
            TelegramMenuMessageFactory messageFactory,
            TrackedItemsMessageHandler trackedItemsHandler,
            TelegramRegionHandler regionHandler,
            TelegramFeedbackHandler feedbackHandler,
            TelegramGateway telegramGateway
    ) {
        return new TelegramMenuHandler(
                messageFactory,
                trackedItemsHandler,
                regionHandler,
                feedbackHandler,
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
    public TelegramFeedbackHandler telegramFeedbackHandler(
            UserProfileService userProfileService,
            FeedbackService feedbackService,
            TelegramGateway telegramGateway,
            FeedbackProperties feedbackProperties,
            Clock providerClock
    ) {
        return new TelegramFeedbackHandler(
                userProfileService, feedbackService, telegramGateway,
                feedbackProperties, providerClock
        );
    }

    @Bean
    public TutorialAssetCatalog tutorialAssetCatalog() {
        return new TutorialAssetCatalog();
    }

    @Bean
    public TelegramTutorialMessageFactory telegramTutorialMessageFactory() {
        return new TelegramTutorialMessageFactory();
    }

    @Bean
    public TelegramTutorialHandler telegramTutorialHandler(
            TutorialAssetCatalog assetCatalog,
            TelegramTutorialMessageFactory messageFactory,
            TelegramGateway telegramGateway,
            TelegramBotProperties telegramProperties
    ) {
        return new TelegramTutorialHandler(
                assetCatalog,
                messageFactory,
                telegramGateway,
                telegramProperties.isTutorialsEnabled()
        );
    }

    @Bean
    public TelegramUpdateDispatcher telegramUpdateDispatcher(
            TelegramOnboardingHandler onboardingHandler,
            TelegramCurrentQuoteHandler currentQuoteHandler,
            TelegramMenuHandler menuHandler,
            TelegramRegionHandler regionHandler,
            TelegramTrackingHandler trackingHandler,
            TelegramSharedBasketHandler sharedBasketHandler,
            TrackedItemsMessageHandler trackedItemsHandler,
            ShowLastKnownCallbackHandler showLastKnownHandler,
            StatisticsCallbackHandler statisticsHandler,
            TelegramFeedbackHandler feedbackHandler,
            TelegramTutorialHandler tutorialHandler,
            TelegramGateway telegramGateway
    ) {
        return new TelegramUpdateDispatcher(
                onboardingHandler,
                currentQuoteHandler,
                menuHandler,
                regionHandler,
                trackingHandler,
                sharedBasketHandler,
                trackedItemsHandler,
                showLastKnownHandler,
                statisticsHandler,
                feedbackHandler,
                tutorialHandler,
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
