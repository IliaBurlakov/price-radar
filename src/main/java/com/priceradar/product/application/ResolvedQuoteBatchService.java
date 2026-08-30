package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceBatchProvider;
import com.priceradar.marketplace.application.MarketplaceBatchProviderResult;
import com.priceradar.marketplace.application.MarketplaceProviderFailure;
import com.priceradar.marketplace.application.MarketplaceProviderFailureCode;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ResolvedQuoteBatchService {

    private final MarketplaceBatchProvider batchProvider;
    private final ResolvedQuoteService resolvedQuoteService;

    public ResolvedQuoteBatchService(
            MarketplaceBatchProvider batchProvider,
            ResolvedQuoteService resolvedQuoteService
    ) {
        if (batchProvider == null || resolvedQuoteService == null) {
            throw new IllegalArgumentException("batch quote dependencies must not be null");
        }
        this.batchProvider = batchProvider;
        this.resolvedQuoteService = resolvedQuoteService;
    }

    public BatchResolvedQuoteResult resolve(
            List<ParsedProductUrl> parsedUrls,
            PriceContext priceContext
    ) {
        if (parsedUrls == null || parsedUrls.isEmpty() || parsedUrls.stream().anyMatch(value -> value == null)
                || priceContext == null) {
            throw new IllegalArgumentException("batch quote input must not be empty");
        }
        List<String> uniqueNmIds = new LinkedHashSet<>(parsedUrls.stream()
                .map(value -> String.valueOf(value.getNmId()))
                .toList()).stream().toList();
        MarketplaceBatchProviderResult providerResult = batchProvider.resolveProducts(
                Marketplace.WILDBERRIES, uniqueNmIds, priceContext
        );
        if (!providerResult.isSuccess()) {
            return BatchResolvedQuoteResult.failure(providerResult.getFailure().orElseThrow());
        }

        Map<String, MarketplaceProviderResult> products = providerResult.getProducts();
        List<BatchResolvedQuoteItem> itemResults = parsedUrls.stream()
                .map(parsedUrl -> resolveOne(parsedUrl, products, priceContext))
                .toList();
        return BatchResolvedQuoteResult.success(itemResults);
    }

    private BatchResolvedQuoteItem resolveOne(
            ParsedProductUrl parsedUrl,
            Map<String, MarketplaceProviderResult> products,
            PriceContext priceContext
    ) {
        MarketplaceProviderResult providerResult = products.get(String.valueOf(parsedUrl.getNmId()));
        if (providerResult == null) {
            return new BatchResolvedQuoteItem(parsedUrl, ResolvedQuoteResult.providerFailure(new MarketplaceProviderFailure(
                    MarketplaceProviderFailureCode.PRODUCT_NOT_FOUND,
                    "Wildberries did not return the requested product",
                    Optional.empty(), UUID.randomUUID().toString()
            )), Optional.empty());
        }
        ResolvedQuoteResult result = resolvedQuoteService.resolve(parsedUrl, providerResult, priceContext);
        return new BatchResolvedQuoteItem(
                parsedUrl,
                result,
                providerResult.getProduct().flatMap(product -> product.getTitle())
        );
    }
}
