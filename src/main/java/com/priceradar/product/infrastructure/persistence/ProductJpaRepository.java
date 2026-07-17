package com.priceradar.product.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByMarketplaceAndExternalProductId(
            Marketplace marketplace,
            long externalProductId
    );
}
