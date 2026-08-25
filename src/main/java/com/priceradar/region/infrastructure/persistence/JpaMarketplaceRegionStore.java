package com.priceradar.region.infrastructure.persistence;

import com.priceradar.region.application.MarketplaceRegionStore;
import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMarketplaceRegionStore implements MarketplaceRegionStore {

    private final MarketplaceRegionJpaRepository repository;

    public JpaMarketplaceRegionStore(MarketplaceRegionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MarketplaceRegion> findEnabled() {
        return repository.findByEnabledTrueOrderBySortOrderAsc().stream().map(this::toRegion).toList();
    }

    @Override
    public Optional<MarketplaceRegion> findByCode(MarketplaceRegionCode code) {
        if (code == null) throw new IllegalArgumentException("region code must not be null");
        return repository.findById(code).map(this::toRegion);
    }

    private MarketplaceRegion toRegion(MarketplaceRegionEntity entity) {
        return new MarketplaceRegion(
                entity.getCode(), entity.getDisplayName(), entity.getWbDestination(),
                entity.getWbSpp(), entity.isEnabled(), entity.getSortOrder()
        );
    }
}
