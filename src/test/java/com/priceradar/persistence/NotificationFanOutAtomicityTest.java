package com.priceradar.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.infrastructure.persistence.ProductEntity;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.scheduler.application.DueWatchTarget;
import com.priceradar.scheduler.application.NotificationFanOutJobStore;
import com.priceradar.scheduler.application.WatchTargetCheckTransaction;
import com.priceradar.testsupport.PostgresTestContainer;
import com.priceradar.tracking.domain.VariantKind;
import com.priceradar.tracking.domain.WatchKey;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetEntity;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationFanOutAtomicityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WatchTargetCheckTransaction checkTransaction;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private WatchTargetJpaRepository watchTargetRepository;

    @Autowired
    private PriceSnapshotJpaRepository snapshotRepository;

    @MockitoSpyBean
    private NotificationFanOutJobStore fanOutJobStore;

    @BeforeEach
    void clearData() {
        snapshotRepository.deleteAll();
        watchTargetRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void jobPersistenceFailureRollsBackSnapshotAndSuccessfulScheduleUpdate() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant originalNextCheckAt = createdAt.plusSeconds(60);
        ProductEntity product = productRepository.save(new ProductEntity(
                UUID.randomUUID(),
                Marketplace.WILDBERRIES,
                123L,
                "https://www.wildberries.ru/catalog/123/detail.aspx",
                "Test product",
                "Test brand",
                createdAt,
                createdAt
        ));
        WatchTargetEntity targetEntity = watchTargetRepository.save(new WatchTargetEntity(
                UUID.randomUUID(),
                product.getId(),
                VariantKind.NO_VARIANT,
                "NO_VARIANT",
                null,
                "Москва",
                1259570991L,
                30,
                originalNextCheckAt,
                createdAt
        ));
        DueWatchTarget target = new DueWatchTarget(
                targetEntity.getId(),
                product.getId(),
                WatchKey.withoutVariant(
                        Marketplace.WILDBERRIES,
                        123L,
                        1259570991L,
                        30
                ),
                originalNextCheckAt
        );
        Instant observedAt = createdAt.plusSeconds(1);
        Instant completedAt = observedAt.plusSeconds(1);
        Instant nextCheckAt = completedAt.plusSeconds(21_600);
        doThrow(new IllegalStateException("fan-out job insert failed"))
                .when(fanOutJobStore).createIfAbsent(any(), eq(completedAt));

        assertThatThrownBy(() -> checkTransaction.persistObservation(
                target,
                UUID.randomUUID(),
                productDetails(),
                regularPrice(),
                observedAt,
                completedAt,
                nextCheckAt
        )).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("fan-out job insert failed");

        assertThat(snapshotRepository.count()).isZero();
        WatchTargetEntity unchanged = watchTargetRepository.findById(targetEntity.getId())
                .orElseThrow();
        assertThat(unchanged.getNextCheckAt()).isEqualTo(originalNextCheckAt);
        assertThat(unchanged.getLastSuccessfulAt()).isNull();
    }

    private MarketplaceProductDetails productDetails() {
        return new MarketplaceProductDetails(
                Marketplace.WILDBERRIES,
                "123",
                Optional.of("Updated product"),
                Optional.of("Updated brand"),
                List.of(),
                Map.of()
        );
    }

    private InterpretedPrice regularPrice() {
        return new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(10_000)),
                Optional.empty(),
                Optional.of(PriceSource.PRODUCT),
                SnapshotStatus.REGULAR_PRICE
        );
    }
}
