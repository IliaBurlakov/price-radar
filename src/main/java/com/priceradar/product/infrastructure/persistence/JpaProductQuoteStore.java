package com.priceradar.product.infrastructure.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.product.application.ProductQuoteStore;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class JpaProductQuoteStore implements ProductQuoteStore {

    private final ProductJpaRepository productRepository;

    public JpaProductQuoteStore(ProductJpaRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public UUID upsert(ResolvedQuotePersistenceCommand command) {
        MarketplaceProductDetails productDetails = command.getProduct();
        productRepository.insertIfAbsent(
                UUID.randomUUID(),
                productDetails.getMarketplace().name(),
                command.getNmId(),
                command.getCanonicalUrl(),
                productDetails.getTitle().orElse(null),
                productDetails.getBrand().orElse(null),
                command.getObservedAt(),
                command.getObservedAt()
        );

        ProductEntity product = productRepository.findByMarketplaceAndExternalProductId(
                productDetails.getMarketplace(),
                command.getNmId()
        ).orElseThrow(() -> new IllegalStateException("Product upsert did not return a product"));

        product.updateMetadata(
                command.getCanonicalUrl(),
                productDetails.getTitle().orElse(null),
                productDetails.getBrand().orElse(null),
                command.getObservedAt()
        );
        return product.getId();
    }
}
