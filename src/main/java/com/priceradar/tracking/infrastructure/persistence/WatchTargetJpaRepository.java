package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.VariantKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WatchTargetJpaRepository extends JpaRepository<WatchTargetEntity, UUID> {

    Optional<WatchTargetEntity> findByProductIdAndVariantKindAndVariantValueAndDestAndSpp(
            UUID productId,
            VariantKind variantKind,
            String variantValue,
            long dest,
            int spp
    );
}
