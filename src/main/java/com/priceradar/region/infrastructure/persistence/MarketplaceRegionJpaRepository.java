package com.priceradar.region.infrastructure.persistence;

import com.priceradar.region.domain.MarketplaceRegionCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplaceRegionJpaRepository
        extends JpaRepository<MarketplaceRegionEntity, MarketplaceRegionCode> {

    List<MarketplaceRegionEntity> findByEnabledTrueOrderBySortOrderAsc();
}
