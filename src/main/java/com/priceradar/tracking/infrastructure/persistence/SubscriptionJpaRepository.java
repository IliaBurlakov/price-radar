package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByUserIdAndWatchTargetIdAndStatus(
            UUID userId,
            UUID watchTargetId,
            SubscriptionStatus status
    );

    Optional<SubscriptionEntity> findByIdAndUserIdAndStatus(
            UUID subscriptionId,
            UUID userId,
            SubscriptionStatus status
    );

    long countByUserIdAndStatus(UUID userId, SubscriptionStatus status);
}
