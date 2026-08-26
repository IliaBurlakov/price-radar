package com.priceradar.region.application;

import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;

import java.util.List;
import java.util.Optional;

public interface MarketplaceRegionStore {

    List<MarketplaceRegion> findEnabled();

    Optional<MarketplaceRegion> findByCode(MarketplaceRegionCode code);
}
