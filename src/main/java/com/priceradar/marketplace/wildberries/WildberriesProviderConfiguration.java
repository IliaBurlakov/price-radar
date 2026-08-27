package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.region.application.WildberriesGeoProvider;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WildberriesProviderProperties.class)
public class WildberriesProviderConfiguration {

    @Bean
    public Clock providerClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HttpClient wildberriesHttpClient(WildberriesProviderProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public WildberriesCardDetailUrlBuilder wildberriesCardDetailUrlBuilder(
            WildberriesProviderProperties properties
    ) {
        return new WildberriesCardDetailUrlBuilder(properties.getEndpoint());
    }

    @Bean
    public WildberriesCardMapper wildberriesCardMapper() {
        return new WildberriesCardMapper();
    }

    @Bean
    public ProviderAccessCoordinator wildberriesProviderAccessCoordinator(
            WildberriesProviderProperties properties,
            Clock providerClock,
            ProviderCooldownStore cooldownStore
    ) {
        return new ProviderAccessCoordinator(
                properties.getMinRequestDelay(),
                providerClock,
                Marketplace.WILDBERRIES,
                cooldownStore
        );
    }

    @Bean
    public WildberriesMarketplaceProvider wildberriesMarketplaceProvider(
            HttpClient wildberriesHttpClient,
            WildberriesCardDetailUrlBuilder urlBuilder,
            WildberriesCardMapper mapper,
            ProviderAccessCoordinator accessCoordinator,
            WildberriesProviderProperties properties,
            Clock providerClock
    ) {
        return new WildberriesMarketplaceProvider(
                wildberriesHttpClient,
                urlBuilder,
                mapper,
                accessCoordinator,
                properties.getRequestTimeout(),
                properties.getBaseBackoff(),
                properties.getMaxBackoff(),
                properties.getMaxAttempts(),
                properties.getCacheTtl(),
                properties.getCacheMaxEntries(),
                properties.getMaxRetryAfter(),
                properties.getMaxResponseBytes(),
                properties.getRateLimitCooldown(),
                properties.getAccessForbiddenCooldown(),
                properties.getServerErrorCooldown(),
                properties.getInvalidResponseCooldown(),
                properties.getMaxBackoffJitter(),
                providerClock
        );
    }

    @Bean
    public WildberriesSharedBasketProvider wildberriesSharedBasketProvider(
            HttpClient wildberriesHttpClient,
            ObjectMapper objectMapper,
            WildberriesCardMapper cardMapper,
            PriceSemanticsService priceSemanticsService,
            ProviderAccessCoordinator accessCoordinator,
            WildberriesProviderProperties properties,
            Clock providerClock
    ) {
        return new WildberriesSharedBasketProvider(
                wildberriesHttpClient,
                properties.getSharedBasketEndpoint(),
                properties.getSharedBasketCardsEndpoint(),
                new WildberriesSharedBasketMapper(objectMapper),
                cardMapper,
                priceSemanticsService,
                accessCoordinator,
                properties.getRequestTimeout(),
                properties.getBaseBackoff(),
                properties.getMaxBackoff(),
                properties.getMaxRetryAfter(),
                properties.getMaxAttempts(),
                properties.getMaxResponseBytes(),
                properties.getBatchSize(),
                properties.getRateLimitCooldown(),
                properties.getServerErrorCooldown(),
                properties.getInvalidResponseCooldown(),
                providerClock
        );
    }

    @Bean
    public WildberriesGeoProvider wildberriesGeoProvider(
            HttpClient wildberriesHttpClient,
            ObjectMapper objectMapper,
            ProviderAccessCoordinator accessCoordinator,
            WildberriesProviderProperties properties,
            Clock providerClock,
            GeoLocationLabelFormatter locationLabelFormatter
    ) {
        return new WildberriesGeoHttpProvider(
                wildberriesHttpClient,
                objectMapper,
                accessCoordinator,
                properties.getGeoEndpoint(),
                properties.getGeoTimeout(),
                properties.getGeoMaxResponseBytes(),
                providerClock,
                locationLabelFormatter
        );
    }
}
