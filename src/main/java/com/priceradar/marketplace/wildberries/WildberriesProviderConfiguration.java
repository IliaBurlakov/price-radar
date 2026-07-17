package com.priceradar.marketplace.wildberries;

import com.priceradar.marketplace.application.ProviderAccessCoordinator;
import com.priceradar.marketplace.application.ProviderCooldownStore;
import com.priceradar.marketplace.domain.Marketplace;
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
                .followRedirects(HttpClient.Redirect.NORMAL)
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
                properties.getMaxAttempts(),
                properties.getCacheTtl(),
                properties.getCacheMaxEntries(),
                providerClock
        );
    }
}
