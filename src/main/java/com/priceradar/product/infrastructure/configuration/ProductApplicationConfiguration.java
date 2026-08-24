package com.priceradar.product.infrastructure.configuration;

import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.product.application.ProductQuoteStore;
import com.priceradar.product.application.ProductUrlParser;
import com.priceradar.product.application.ResolvedQuoteService;
import com.priceradar.product.application.ResolvedQuotePersistenceService;
import com.priceradar.product.application.ResolvedQuoteStore;
import com.priceradar.product.application.VariantResolutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProductApplicationConfiguration {

    @Bean
    public ProductUrlParser productUrlParser() {
        return new ProductUrlParser();
    }

    @Bean
    public VariantResolutionService variantResolutionService() {
        return new VariantResolutionService();
    }

    @Bean
    public PriceSemanticsService priceSemanticsService() {
        return new PriceSemanticsService();
    }

    @Bean
    public ResolvedQuotePersistenceService resolvedQuotePersistenceService(
            ProductQuoteStore productStore,
            ResolvedQuoteStore quoteStore
    ) {
        return new ResolvedQuotePersistenceService(productStore, quoteStore);
    }

    @Bean
    public ResolvedQuoteService resolvedQuoteService(
            ProductUrlParser productUrlParser,
            MarketplaceProvider marketplaceProvider,
            VariantResolutionService variantResolutionService,
            PriceSemanticsService priceSemanticsService,
            ResolvedQuotePersistenceService persistenceService
    ) {
        return new ResolvedQuoteService(
                productUrlParser,
                marketplaceProvider,
                variantResolutionService,
                priceSemanticsService,
                persistenceService
        );
    }
}
