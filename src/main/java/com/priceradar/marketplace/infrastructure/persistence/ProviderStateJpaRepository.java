package com.priceradar.marketplace.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderStateJpaRepository extends JpaRepository<ProviderStateEntity, Marketplace> {
}
