package com.priceradar.region.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.region.application.CitySelectionService;
import com.priceradar.region.application.GeoCandidateDeduplicator;
import com.priceradar.region.application.GeoLocationCatalog;
import com.priceradar.region.application.GeoLocationLabelFormatter;
import com.priceradar.region.application.GeocodingProvider;
import com.priceradar.region.application.PendingCitySelectionStore;
import com.priceradar.region.application.UserRegionService;
import com.priceradar.region.application.WildberriesGeoProvider;
import com.priceradar.region.infrastructure.geocoding.NominatimGeocodingProvider;
import com.priceradar.region.infrastructure.geocoding.NominatimProperties;
import com.priceradar.region.infrastructure.geocoding.NominatimRequestCoordinator;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({NominatimProperties.class, RegionSelectionProperties.class})
public class RegionConfiguration {

    @Bean
    public UserRegionService userRegionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        return new UserRegionService(userProfileStore, subscriptionStore);
    }

    @Bean
    public GeoCandidateDeduplicator geoCandidateDeduplicator() {
        return new GeoCandidateDeduplicator();
    }

    @Bean
    public GeoLocationLabelFormatter geoLocationLabelFormatter() {
        return new GeoLocationLabelFormatter();
    }

    @Bean
    public NominatimRequestCoordinator nominatimRequestCoordinator(
            NominatimProperties properties,
            Clock providerClock
    ) {
        return new NominatimRequestCoordinator(properties.getMinRequestInterval(), providerClock);
    }

    @Bean
    public GeocodingProvider geocodingProvider(
            NominatimProperties properties,
            ObjectMapper objectMapper,
            NominatimRequestCoordinator coordinator
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new NominatimGeocodingProvider(client, objectMapper, coordinator, properties);
    }

    @Bean
    public CitySelectionService citySelectionService(
            GeocodingProvider geocodingProvider,
            WildberriesGeoProvider wildberriesGeoProvider,
            GeoLocationCatalog locationCatalog,
            PendingCitySelectionStore pendingStore,
            GeoCandidateDeduplicator deduplicator,
            UserRegionService userRegionService,
            SubscriptionStore subscriptionStore,
            RegionSelectionProperties properties
    ) {
        return new CitySelectionService(
                geocodingProvider, wildberriesGeoProvider, locationCatalog, pendingStore,
                deduplicator, userRegionService, subscriptionStore, properties.getSessionTtl()
        );
    }
}
