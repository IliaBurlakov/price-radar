package com.priceradar.region.infrastructure.persistence;

import com.priceradar.region.domain.MarketplaceRegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_regions")
public class MarketplaceRegionEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 32)
    private MarketplaceRegionCode code;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "wb_destination", nullable = false)
    private long wbDestination;

    @Column(name = "wb_spp", nullable = false)
    private int wbSpp;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MarketplaceRegionEntity() {
    }

    public MarketplaceRegionCode getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getWbDestination() {
        return wbDestination;
    }

    public int getWbSpp() {
        return wbSpp;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
