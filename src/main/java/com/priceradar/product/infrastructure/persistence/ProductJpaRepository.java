package com.priceradar.product.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByMarketplaceAndExternalProductId(
            Marketplace marketplace,
            long externalProductId
    );

    @Modifying
    @Query(value = """
            INSERT INTO products (
                id, marketplace, external_product_id, canonical_url,
                title, brand, metadata_updated_at, created_at
            ) VALUES (
                :id, :marketplace, :externalProductId, :canonicalUrl,
                :title, :brand, :metadataUpdatedAt, :createdAt
            )
            ON CONFLICT (marketplace, external_product_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("marketplace") String marketplace,
            @Param("externalProductId") long externalProductId,
            @Param("canonicalUrl") String canonicalUrl,
            @Param("title") String title,
            @Param("brand") String brand,
            @Param("metadataUpdatedAt") Instant metadataUpdatedAt,
            @Param("createdAt") Instant createdAt
    );
}
