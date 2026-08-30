package com.priceradar.sharedbasket.infrastructure.configuration;

import com.priceradar.configuration.PriceRadarPolicyProperties;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.sharedbasket.application.PendingSharedBasketImportStore;
import com.priceradar.sharedbasket.application.SharedBasketImportService;
import com.priceradar.sharedbasket.application.SharedBasketProductResolver;
import com.priceradar.sharedbasket.application.SharedBasketProvider;
import com.priceradar.sharedbasket.application.SharedBasketUrlParser;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SharedBasketConfiguration {

    @Bean
    public SharedBasketUrlParser sharedBasketUrlParser() {
        return new SharedBasketUrlParser();
    }

    @Bean
    public SharedBasketImportService sharedBasketImportService(
            SharedBasketUrlParser urlParser,
            SharedBasketProvider basketProvider,
            SharedBasketProductResolver productResolver,
            PriceSemanticsService priceSemanticsService,
            ResolvedQuotePersistenceService quotePersistenceService,
            PendingSharedBasketImportStore pendingStore,
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore,
            PriceRadarPolicyProperties policy
    ) {
        return new SharedBasketImportService(
                urlParser, basketProvider, productResolver, priceSemanticsService,
                quotePersistenceService, pendingStore, userProfileStore, subscriptionStore,
                policy.getActiveSubscriptionLimit(), policy.getSharedBasketImportTtl()
        );
    }
}
