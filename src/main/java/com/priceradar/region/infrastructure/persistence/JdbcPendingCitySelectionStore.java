package com.priceradar.region.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.region.application.PendingCitySelection;
import com.priceradar.region.application.PendingCitySelectionStore;
import com.priceradar.region.domain.GeoCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPendingCitySelectionStore implements PendingCitySelectionStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPendingCitySelectionStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PendingCitySelection replace(PendingCitySelection selection, Instant now) {
        if (selection == null || now == null) throw new IllegalArgumentException("selection fields must not be null");
        deleteExpired(now);
        jdbcTemplate.update("""
                INSERT INTO pending_city_selections (id, user_id, candidates, created_at, expires_at)
                VALUES (?, ?, CAST(? AS JSONB), ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET id = EXCLUDED.id,
                    candidates = EXCLUDED.candidates,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                """,
                selection.getId(), selection.getUserId(), serialize(selection.getCandidates()),
                Timestamp.from(selection.getCreatedAt()), Timestamp.from(selection.getExpiresAt())
        );
        return selection;
    }

    @Override
    @Transactional
    public Optional<PendingCitySelection> findByUserId(UUID userId, Instant now) {
        if (userId == null || now == null) return Optional.empty();
        deleteExpired(now);
        return jdbcTemplate.query("""
                SELECT id, user_id, candidates::text, created_at, expires_at
                FROM pending_city_selections
                WHERE user_id = ? AND expires_at > ?
                """,
                (resultSet, row) -> new PendingCitySelection(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("user_id", UUID.class),
                        deserialize(resultSet.getString("candidates")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant()
                ),
                userId, Timestamp.from(now)
        ).stream().findFirst();
    }

    @Override
    public void remove(UUID userId) {
        if (userId != null) jdbcTemplate.update("DELETE FROM pending_city_selections WHERE user_id = ?", userId);
    }

    private void deleteExpired(Instant now) {
        jdbcTemplate.update("DELETE FROM pending_city_selections WHERE expires_at <= ?", Timestamp.from(now));
    }

    private String serialize(List<GeoCandidate> candidates) {
        ArrayNode root = objectMapper.createArrayNode();
        for (GeoCandidate candidate : candidates) {
            ObjectNode node = root.addObject();
            node.put("settlementName", candidate.getSettlementName());
            candidate.getRegionName().ifPresent(value -> node.put("regionName", value));
            candidate.getDistrictName().ifPresent(value -> node.put("districtName", value));
            node.put("countryName", candidate.getCountryName());
            node.put("latitude", candidate.getLatitude());
            node.put("longitude", candidate.getLongitude());
            candidate.getProviderObjectType().ifPresent(value -> node.put("providerObjectType", value));
            candidate.getProviderObjectId().ifPresent(value -> node.put("providerObjectId", value));
            candidate.getCategory().ifPresent(value -> node.put("category", value));
            candidate.getPlaceType().ifPresent(value -> node.put("placeType", value));
            node.put("importance", candidate.getImportance());
            node.put("placeRank", candidate.getPlaceRank());
        }
        return root.toString();
    }

    private List<GeoCandidate> deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) throw new IllegalStateException("pending city candidates must be an array");
            List<GeoCandidate> candidates = new ArrayList<>();
            for (JsonNode node : root) {
                candidates.add(new GeoCandidate(
                        text(node, "settlementName"), text(node, "regionName"),
                        text(node, "districtName"), text(node, "countryName"),
                        new BigDecimal(text(node, "latitude")), new BigDecimal(text(node, "longitude")),
                        text(node, "providerObjectType"), text(node, "providerObjectId"),
                        text(node, "category"), text(node, "placeType"),
                        node.path("importance").asDouble(0), node.path("placeRank").asInt(0)
                ));
            }
            return List.copyOf(candidates);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read pending city selection", exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }
}
