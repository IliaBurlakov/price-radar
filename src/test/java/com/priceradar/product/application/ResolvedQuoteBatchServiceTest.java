package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceBatchProvider;
import com.priceradar.marketplace.application.MarketplaceBatchProviderResult;
import com.priceradar.marketplace.application.MarketplaceProviderResult;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResolvedQuoteBatchServiceTest {

    @Test
    void fetchesSameNmIdOnceAndResolvesDifferentExplicitSizesIndependently() {
        MarketplaceBatchProvider provider = mock(MarketplaceBatchProvider.class);
        ResolvedQuoteService singleResolution = mock(ResolvedQuoteService.class);
        ResolvedQuoteBatchService service = new ResolvedQuoteBatchService(provider, singleResolution);
        PriceContext context = new PriceContext("Moscow", 1259570991L, 30);
        ParsedProductUrl firstSize = new ParsedProductUrl(111, OptionalLong.of(10));
        ParsedProductUrl secondSize = new ParsedProductUrl(111, OptionalLong.of(20));
        MarketplaceProviderResult product = mock(MarketplaceProviderResult.class);
        ResolvedQuoteResult first = mock(ResolvedQuoteResult.class);
        ResolvedQuoteResult second = mock(ResolvedQuoteResult.class);
        when(provider.resolveProducts(Marketplace.WILDBERRIES, List.of("111"), context))
                .thenReturn(MarketplaceBatchProviderResult.success(Map.of("111", product)));
        when(singleResolution.resolve(firstSize, product, context)).thenReturn(first);
        when(singleResolution.resolve(secondSize, product, context)).thenReturn(second);

        BatchResolvedQuoteResult result = service.resolve(List.of(firstSize, secondSize), context);

        assertThat(result.getItemResults()).extracting(BatchResolvedQuoteItem::getResult)
                .containsExactly(first, second);
        verify(provider).resolveProducts(Marketplace.WILDBERRIES, List.of("111"), context);
        verify(singleResolution, times(2)).resolve(any(ParsedProductUrl.class), any(), any());
    }
}
