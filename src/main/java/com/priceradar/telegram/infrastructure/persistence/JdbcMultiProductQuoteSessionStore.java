package com.priceradar.telegram.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuote;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.application.VariantAttribute;
import com.priceradar.telegram.application.MultiProductQuoteItem;
import com.priceradar.telegram.application.MultiProductQuoteSession;
import com.priceradar.telegram.application.MultiProductQuoteSessionStore;
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
public class JdbcMultiProductQuoteSessionStore implements MultiProductQuoteSessionStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMultiProductQuoteSessionStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    @Override
    @Transactional
    public void save(MultiProductQuoteSession session) {
        jdbcTemplate.update(
                "DELETE FROM pending_multi_product_quote_sessions WHERE expires_at <= ?",
                Timestamp.from(session.getCreatedAt())
        );
        jdbcTemplate.update("""
                INSERT INTO pending_multi_product_quote_sessions (id, user_id, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, session.getId(), session.getUserId(),
                Timestamp.from(session.getCreatedAt()), Timestamp.from(session.getExpiresAt()));
        for (MultiProductQuoteItem item : session.getItems()) {
            jdbcTemplate.update("""
                    INSERT INTO pending_multi_product_quote_items (
                        session_id, item_position, nm_id, display_name,
                        resolution_status, quote_payload
                    ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB))
                    """,
                    session.getId(), item.getPosition(), item.getNmId(), item.getDisplayName(),
                    item.getStatus().name(), item.getQuote().map(this::writeQuote).orElse(null)
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MultiProductQuoteSession> findOwned(UUID sessionId, UUID userId) {
        List<SessionHeader> headers = jdbcTemplate.query("""
                SELECT id, user_id, created_at, expires_at
                FROM pending_multi_product_quote_sessions
                WHERE id = ? AND user_id = ?
                """, (resultSet, rowNum) -> new SessionHeader(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                resultSet.getObject("expires_at", java.time.OffsetDateTime.class).toInstant()
        ), sessionId, userId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        SessionHeader header = headers.getFirst();
        List<MultiProductQuoteItem> items = jdbcTemplate.query("""
                SELECT item_position, nm_id, display_name, resolution_status, quote_payload
                FROM pending_multi_product_quote_items
                WHERE session_id = ?
                ORDER BY item_position
                """, this::readItem, sessionId);
        return Optional.of(new MultiProductQuoteSession(
                header.id, header.userId, header.createdAt, header.expiresAt, items
        ));
    }

    private MultiProductQuoteItem readItem(ResultSet resultSet, int rowNum) throws SQLException {
        String payload = resultSet.getString("quote_payload");
        return new MultiProductQuoteItem(
                resultSet.getInt("item_position"),
                resultSet.getLong("nm_id"),
                resultSet.getString("display_name"),
                MultiProductQuoteItem.Status.valueOf(resultSet.getString("resolution_status")),
                payload == null ? Optional.empty() : Optional.of(readQuote(payload))
        );
    }

    private String writeQuote(ResolvedQuote quote) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("watchTargetId", quote.getWatchTargetId().toString());
        root.put("quoteSnapshotId", quote.getQuoteSnapshotId().toString());
        root.put("marketplace", quote.getMarketplace().name());
        root.put("nmId", quote.getNmId());
        root.put("canonicalUrl", quote.getCanonicalUrl());
        quote.getTitle().ifPresent(value -> root.put("title", value));
        quote.getBrand().ifPresent(value -> root.put("brand", value));
        root.put("variantKey", quote.getResolvedVariant().getVariantKey());
        root.put("autoSelected", quote.getResolvedVariant().isAutoSelected());
        ArrayNode attributes = root.putArray("variantAttributes");
        quote.getResolvedVariant().getAttributes().forEach(attribute -> {
            ObjectNode node = attributes.addObject();
            node.put("name", attribute.getName());
            node.put("value", attribute.getValue());
        });
        InterpretedPrice price = quote.getInterpretedPrice();
        root.put("snapshotStatus", price.getStatus().name());
        price.getPriceSource().ifPresent(value -> root.put("priceSource", value.name()));
        price.getRegularPrice().ifPresent(value -> root.put("regularPriceMinor", value.getMinorUnits()));
        price.getMarketingBasePrice().ifPresent(value -> root.put("marketingBasePriceMinor", value.getMinorUnits()));
        root.put("cityName", quote.getPriceContext().getCityName());
        root.put("dest", quote.getPriceContext().getDest());
        root.put("spp", quote.getPriceContext().getSpp());
        root.put("observedAt", quote.getObservedAt().toString());
        root.put("expiresAt", quote.getExpiresAt().toString());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist multi-product quote", exception);
        }
    }

    private ResolvedQuote readQuote(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            List<VariantAttribute> attributes = new ArrayList<>();
            root.path("variantAttributes").forEach(node -> attributes.add(new VariantAttribute(
                    node.path("name").asText(), node.path("value").asText()
            )));
            SnapshotStatus status = SnapshotStatus.valueOf(root.path("snapshotStatus").asText());
            Optional<PriceSource> source = root.has("priceSource")
                    ? Optional.of(PriceSource.valueOf(root.path("priceSource").asText())) : Optional.empty();
            Optional<RubleAmount> regular = amount(root, "regularPriceMinor");
            Optional<RubleAmount> base = amount(root, "marketingBasePriceMinor");
            return new ResolvedQuote(
                    UUID.fromString(root.path("watchTargetId").asText()),
                    UUID.fromString(root.path("quoteSnapshotId").asText()),
                    Marketplace.valueOf(root.path("marketplace").asText()),
                    root.path("nmId").asLong(),
                    root.path("canonicalUrl").asText(),
                    optionalText(root, "title"),
                    optionalText(root, "brand"),
                    new ResolvedVariant(
                            root.path("variantKey").asText(), attributes, root.path("autoSelected").asBoolean()
                    ),
                    new InterpretedPrice(regular, base, source, status),
                    new PriceContext(
                            root.path("cityName").asText(), root.path("dest").asLong(), root.path("spp").asInt()
                    ),
                    Instant.parse(root.path("observedAt").asText()),
                    Instant.parse(root.path("expiresAt").asText())
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored multi-product quote is invalid", exception);
        }
    }

    private Optional<RubleAmount> amount(JsonNode root, String field) {
        return root.has(field) ? Optional.of(RubleAmount.ofMinorUnits(root.path(field).asLong())) : Optional.empty();
    }

    private Optional<String> optionalText(JsonNode root, String field) {
        return root.has(field) ? Optional.of(root.path(field).asText()) : Optional.empty();
    }

    private static final class SessionHeader {
        private final UUID id;
        private final UUID userId;
        private final Instant createdAt;
        private final Instant expiresAt;

        private SessionHeader(UUID id, UUID userId, Instant createdAt, Instant expiresAt) {
            this.id = id;
            this.userId = userId;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }
}
