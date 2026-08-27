package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.application.MarketplaceProductRequest;
import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.PriceContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class ResolvedQuoteService {

    private static final String CANONICAL_URL_TEMPLATE =
            "https://www.wildberries.ru/catalog/%d/detail.aspx";

    private final ProductUrlParser productUrlParser;
    private final MarketplaceProvider marketplaceProvider;
    private final VariantResolutionService variantResolutionService;
    private final PriceSemanticsService priceSemanticsService;
    private final ResolvedQuotePersistenceService persistenceService;
    private final Duration quoteTtl;
    public ResolvedQuoteService(
            ProductUrlParser productUrlParser,
            MarketplaceProvider marketplaceProvider,
            VariantResolutionService variantResolutionService,
            PriceSemanticsService priceSemanticsService,
            ResolvedQuotePersistenceService persistenceService,
            Duration quoteTtl
    ) {
        if (productUrlParser == null || marketplaceProvider == null || variantResolutionService == null
                || priceSemanticsService == null || persistenceService == null || quoteTtl == null) {
            throw new IllegalArgumentException("resolved quote service dependencies must not be null");
        }
        if (quoteTtl.isZero() || quoteTtl.isNegative()) {
            throw new IllegalArgumentException("quote TTL must be positive");
        }

        this.productUrlParser = productUrlParser;
        this.marketplaceProvider = marketplaceProvider;
        this.variantResolutionService = variantResolutionService;
        this.priceSemanticsService = priceSemanticsService;
        this.persistenceService = persistenceService;
        this.quoteTtl = quoteTtl;
    }

    public ResolvedQuoteResult resolve(String productUrl, PriceContext priceContext) {
        if (priceContext == null) {
            throw new IllegalArgumentException("priceContext must not be null");
        }

        ParsedProductUrl parsedUrl = productUrlParser.parse(productUrl);
        MarketplaceProviderResult providerResult = marketplaceProvider.resolveProduct(
                new MarketplaceProductRequest(
                        Marketplace.WILDBERRIES,
                        String.valueOf(parsedUrl.getNmId()),
                        priceContext
                )
        );

        return resolve(parsedUrl, providerResult, priceContext);
    }

    ResolvedQuoteResult resolve(
            ParsedProductUrl parsedUrl,
            MarketplaceProviderResult providerResult,
            PriceContext priceContext
    ) {
        if (parsedUrl == null || providerResult == null || priceContext == null) {
            throw new IllegalArgumentException("resolved quote input must not be null");
        }

        if (!providerResult.isSuccess()) {
            return ResolvedQuoteResult.providerFailure(
                    providerResult.getFailure().orElseThrow()
            );
        }

        MarketplaceProductDetails product = providerResult.getProduct().orElseThrow();
        if (!matchesRequestedProduct(product, parsedUrl.getNmId())) {
            return ResolvedQuoteResult.failure(
                    ResolvedQuoteResult.FailureCode.PROVIDER_DATA_MISMATCH,
                    "Provider returned a different product"
            );
        }

        Optional<ResolvedVariant> resolvedVariant = variantResolutionService.resolveInitial(
                parsedUrl,
                product.getVariantOptions()
        );
        if (resolvedVariant.isEmpty()) {
            return ResolvedQuoteResult.failure(
                    ResolvedQuoteResult.FailureCode.VARIANT_NOT_RESOLVED,
                    "Requested product variant is unavailable"
            );
        }

        ProviderPriceFields priceFields = product.getPriceFieldsByVariantKey()
                .get(resolvedVariant.get().getVariantKey());
        if (priceFields == null) {
            return ResolvedQuoteResult.failure(
                    ResolvedQuoteResult.FailureCode.PRICE_FIELDS_NOT_FOUND,
                    "Provider did not return price fields for the resolved variant"
            );
        }

        InterpretedPrice interpretedPrice = priceSemanticsService.interpret(priceFields);
        Instant observedAt = providerResult.getObservedAt().orElseThrow();
        String canonicalUrl = CANONICAL_URL_TEMPLATE.formatted(parsedUrl.getNmId());

        ResolvedQuotePersistenceCommand persistenceCommand = new ResolvedQuotePersistenceCommand(
                product,
                parsedUrl.getNmId(),
                canonicalUrl,
                resolvedVariant.get(),
                priceContext,
                interpretedPrice,
                observedAt
        );
        PersistedResolvedQuote persistedQuote = persistenceService.save(persistenceCommand);

        return ResolvedQuoteResult.success(new ResolvedQuote(
                persistedQuote.getWatchTargetId(),
                persistedQuote.getSnapshotId(),
                product.getMarketplace(),
                parsedUrl.getNmId(),
                canonicalUrl,
                product.getTitle(),
                product.getBrand(),
                resolvedVariant.get(),
                interpretedPrice,
                priceContext,
                observedAt,
                observedAt.plus(quoteTtl)
        ));
    }

    private boolean matchesRequestedProduct(MarketplaceProductDetails product, long nmId) {
        if (product.getMarketplace() != Marketplace.WILDBERRIES) {
            return false;
        }
        return product.getExternalProductId().equals(String.valueOf(nmId));
    }
}
