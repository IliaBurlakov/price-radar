package com.priceradar.region.infrastructure.persistence;

import com.priceradar.region.application.GeoLocationCatalog;
import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.GeoLocationSource;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGeoLocationCatalog implements GeoLocationCatalog {

    private static final String LOCATION_COLUMNS = """
            location.id, location.settlement_name, location.region_name,
            location.district_name, location.country_name, location.latitude,
            location.longitude, location.source, location.provider_object_type,
            location.provider_object_id, location.created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcGeoLocationCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<GeoLocation>> findCompletedSearch(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) return Optional.empty();
        Integer completed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM geo_location_searches WHERE normalized_query = ?",
                Integer.class,
                normalizedQuery
        );
        if (completed == null || completed == 0) return Optional.empty();
        return Optional.of(jdbcTemplate.query(
                "SELECT " + LOCATION_COLUMNS + """
                        FROM geo_location_search_candidates candidate
                        JOIN geo_locations location ON location.id = candidate.location_id
                        WHERE candidate.normalized_query = ?
                        ORDER BY candidate.candidate_position
                        """,
                this::mapLocation,
                normalizedQuery
        ));
    }

    @Override
    @Transactional
    public List<GeoLocation> saveCompletedSearch(
            String normalizedQuery,
            List<GeoCandidate> candidates,
            Instant completedAt
    ) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || candidates == null
                || completedAt == null || candidates.stream().anyMatch(candidate -> candidate == null)) {
            throw new IllegalArgumentException("completed geo search fields are invalid");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO geo_location_searches (normalized_query, completed_at)
                VALUES (?, ?)
                ON CONFLICT (normalized_query) DO NOTHING
                """, normalizedQuery, Timestamp.from(completedAt));
        if (inserted == 0) {
            return findCompletedSearch(normalizedQuery).orElseThrow(
                    () -> new IllegalStateException("Completed geo search is unavailable")
            );
        }
        List<GeoLocation> locations = new ArrayList<>();
        for (int position = 0; position < candidates.size(); position++) {
            GeoLocation location = saveLocation(candidates.get(position), completedAt);
            locations.add(location);
            jdbcTemplate.update("""
                    INSERT INTO geo_location_search_candidates (
                        normalized_query, location_id, candidate_position
                    ) VALUES (?, ?, ?)
                    """, normalizedQuery, location.getId(), position);
        }
        return List.copyOf(locations);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedLocation> findResolvedByIdentityKey(String identityKey) {
        if (identityKey == null || identityKey.isBlank()) return Optional.empty();
        return jdbcTemplate.query(
                resolvedSelect() + " WHERE location.identity_key = ?",
                this::mapResolved,
                identityKey
        ).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedLocation> findById(UUID locationId) {
        if (locationId == null) return Optional.empty();
        return jdbcTemplate.query(
                resolvedSelect() + " WHERE location.id = ?",
                this::mapResolved,
                locationId
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public ResolvedLocation saveResolved(
            GeoCandidate candidate,
            WildberriesLocationContext context,
            Instant createdAt
    ) {
        if (candidate == null || context == null || createdAt == null) {
            throw new IllegalArgumentException("resolved location persistence fields must not be null");
        }
        GeoLocation location = findLocationByIdentityKey(candidate.identityKey())
                .orElseGet(() -> saveLocation(candidate, createdAt));
        jdbcTemplate.update("""
                INSERT INTO wildberries_location_contexts (location_id, dest, spp, resolved_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (location_id) DO NOTHING
                """,
                location.getId(), context.getDestination(), context.getSpp(),
                Timestamp.from(context.getResolvedAt())
        );
        return findById(location.getId()).orElseThrow(
                () -> new IllegalStateException("Resolved geo location is unavailable after persistence")
        );
    }

    private GeoLocation saveLocation(GeoCandidate candidate, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO geo_locations (
                    id, settlement_name, region_name, district_name, country_name,
                    latitude, longitude, normalized_name, normalized_region, normalized_district,
                    identity_key, source, provider_object_type, provider_object_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOMINATIM', ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                UUID.randomUUID(), candidate.getSettlementName(), candidate.getRegionName().orElse(null),
                candidate.getDistrictName().orElse(null), candidate.getCountryName(),
                candidate.getLatitude(), candidate.getLongitude(), candidate.getNormalizedName(),
                candidate.getNormalizedRegion(), candidate.getNormalizedDistrict(), candidate.identityKey(),
                candidate.getProviderObjectType().orElse(null), candidate.getProviderObjectId().orElse(null),
                Timestamp.from(createdAt)
        );
        return findLocationByIdentityKey(candidate.identityKey())
                .orElseGet(() -> findLocationByProviderIdentity(candidate).orElseThrow(
                        () -> new IllegalStateException("Geo location insert did not produce a readable row")
                ));
    }

    private Optional<GeoLocation> findLocationByIdentityKey(String identityKey) {
        return jdbcTemplate.query(
                "SELECT " + LOCATION_COLUMNS + " FROM geo_locations location WHERE location.identity_key = ?",
                this::mapLocation,
                identityKey
        ).stream().findFirst();
    }

    private Optional<GeoLocation> findLocationByProviderIdentity(GeoCandidate candidate) {
        if (candidate.getProviderObjectType().isEmpty() || candidate.getProviderObjectId().isEmpty()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT " + LOCATION_COLUMNS + """
                        FROM geo_locations location
                        WHERE location.source = 'NOMINATIM'
                          AND location.provider_object_type = ?
                          AND location.provider_object_id = ?
                        """,
                this::mapLocation,
                candidate.getProviderObjectType().orElseThrow(),
                candidate.getProviderObjectId().orElseThrow()
        ).stream().findFirst();
    }

    private String resolvedSelect() {
        return "SELECT " + LOCATION_COLUMNS + """
                , context.dest, context.spp, context.resolved_at
                FROM geo_locations location
                JOIN wildberries_location_contexts context ON context.location_id = location.id
                """;
    }

    private GeoLocation mapLocation(ResultSet resultSet, int rowNumber) throws SQLException {
        GeoCandidate candidate = new GeoCandidate(
                resultSet.getString("settlement_name"), resultSet.getString("region_name"),
                resultSet.getString("district_name"), resultSet.getString("country_name"),
                resultSet.getBigDecimal("latitude"), resultSet.getBigDecimal("longitude"),
                resultSet.getString("provider_object_type"), resultSet.getString("provider_object_id"),
                "", "", 0, 0
        );
        return new GeoLocation(
                resultSet.getObject("id", UUID.class), candidate,
                GeoLocationSource.valueOf(resultSet.getString("source")),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private ResolvedLocation mapResolved(ResultSet resultSet, int rowNumber) throws SQLException {
        GeoLocation location = mapLocation(resultSet, rowNumber);
        return new ResolvedLocation(location, new WildberriesLocationContext(
                resultSet.getLong("dest"), resultSet.getInt("spp"),
                resultSet.getTimestamp("resolved_at").toInstant()
        ));
    }
}
