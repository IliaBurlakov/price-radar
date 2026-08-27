CREATE TABLE geo_locations (
    id UUID PRIMARY KEY,
    settlement_name VARCHAR(100) NOT NULL,
    region_name VARCHAR(160),
    district_name VARCHAR(200),
    country_name VARCHAR(100) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    normalized_region VARCHAR(160) NOT NULL DEFAULT '',
    normalized_district VARCHAR(200) NOT NULL DEFAULT '',
    identity_key VARCHAR(600) NOT NULL UNIQUE,
    source VARCHAR(32) NOT NULL,
    provider_object_type VARCHAR(32),
    provider_object_id VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_geo_locations_settlement CHECK (btrim(settlement_name) <> ''),
    CONSTRAINT ck_geo_locations_country CHECK (btrim(country_name) <> ''),
    CONSTRAINT ck_geo_locations_normalized_name CHECK (btrim(normalized_name) <> ''),
    CONSTRAINT ck_geo_locations_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_geo_locations_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_geo_locations_source CHECK (source IN ('NOMINATIM', 'LEGACY'))
);

CREATE INDEX idx_geo_locations_normalized_name
    ON geo_locations (normalized_name);

CREATE UNIQUE INDEX uk_geo_locations_provider_identity
    ON geo_locations (source, provider_object_type, provider_object_id)
    WHERE provider_object_type IS NOT NULL AND provider_object_id IS NOT NULL;

CREATE TABLE geo_location_searches (
    normalized_query VARCHAR(160) PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_geo_location_searches_query CHECK (btrim(normalized_query) <> '')
);

CREATE TABLE geo_location_search_candidates (
    normalized_query VARCHAR(160) NOT NULL
        REFERENCES geo_location_searches(normalized_query) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES geo_locations(id) ON DELETE CASCADE,
    candidate_position INTEGER NOT NULL,
    PRIMARY KEY (normalized_query, location_id),
    CONSTRAINT uk_geo_location_search_candidate_position
        UNIQUE (normalized_query, candidate_position),
    CONSTRAINT ck_geo_location_search_candidate_position CHECK (candidate_position >= 0)
);

CREATE TABLE wildberries_location_contexts (
    location_id UUID PRIMARY KEY REFERENCES geo_locations(id) ON DELETE CASCADE,
    dest BIGINT NOT NULL,
    spp INTEGER NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_wb_location_context_dest CHECK (dest <> 0),
    CONSTRAINT ck_wb_location_context_spp CHECK (spp >= 0)
);

INSERT INTO geo_locations (
    id, settlement_name, region_name, district_name, country_name,
    latitude, longitude, normalized_name, normalized_region, normalized_district,
    identity_key, source, created_at
) VALUES
    ('00000000-0000-0000-0000-000000000101', 'Москва', 'Москва', NULL, 'Россия', 55.755800, 37.617300, 'москва', 'москва', '', 'москва|москва||55.756|37.617', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000102', 'Санкт-Петербург', 'Санкт-Петербург', NULL, 'Россия', 59.938600, 30.314100, 'санкт-петербург', 'санкт-петербург', '', 'санкт-петербург|санкт-петербург||59.939|30.314', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000103', 'Екатеринбург', 'Свердловская область', NULL, 'Россия', 56.838900, 60.605700, 'екатеринбург', 'свердловская область', '', 'екатеринбург|свердловская область||56.839|60.606', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000104', 'Новосибирск', 'Новосибирская область', NULL, 'Россия', 55.028200, 82.920400, 'новосибирск', 'новосибирская область', '', 'новосибирск|новосибирская область||55.028|82.920', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000105', 'Красноярск', 'Красноярский край', NULL, 'Россия', 56.010600, 92.852600, 'красноярск', 'красноярский край', '', 'красноярск|красноярский край||56.011|92.853', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000106', 'Иркутск', 'Иркутская область', NULL, 'Россия', 52.286400, 104.280700, 'иркутск', 'иркутская область', '', 'иркутск|иркутская область||52.286|104.281', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000107', 'Братск', 'Иркутская область', NULL, 'Россия', 56.151400, 101.633500, 'братск', 'иркутская область', '', 'братск|иркутская область||56.151|101.634', 'LEGACY', NOW()),
    ('00000000-0000-0000-0000-000000000108', 'Чита', 'Забайкальский край', NULL, 'Россия', 52.034000, 113.499400, 'чита', 'забайкальский край', '', 'чита|забайкальский край||52.034|113.499', 'LEGACY', NOW());

INSERT INTO wildberries_location_contexts (location_id, dest, spp, resolved_at) VALUES
    ('00000000-0000-0000-0000-000000000101', 1259570991, 30, NOW()),
    ('00000000-0000-0000-0000-000000000102', -1123299, 30, NOW()),
    ('00000000-0000-0000-0000-000000000103', 123589409, 30, NOW()),
    ('00000000-0000-0000-0000-000000000104', -366519, 30, NOW()),
    ('00000000-0000-0000-0000-000000000105', -5854093, 30, NOW()),
    ('00000000-0000-0000-0000-000000000106', -5827722, 30, NOW()),
    ('00000000-0000-0000-0000-000000000107', 123586041, 30, NOW()),
    ('00000000-0000-0000-0000-000000000108', -5551586, 30, NOW());

ALTER TABLE user_profiles ADD COLUMN location_id UUID REFERENCES geo_locations(id);

UPDATE user_profiles
SET location_id = CASE region_code
    WHEN 'MOSCOW' THEN '00000000-0000-0000-0000-000000000101'::UUID
    WHEN 'SAINT_PETERSBURG' THEN '00000000-0000-0000-0000-000000000102'::UUID
    WHEN 'EKATERINBURG' THEN '00000000-0000-0000-0000-000000000103'::UUID
    WHEN 'NOVOSIBIRSK' THEN '00000000-0000-0000-0000-000000000104'::UUID
    WHEN 'KRASNOYARSK' THEN '00000000-0000-0000-0000-000000000105'::UUID
    WHEN 'IRKUTSK' THEN '00000000-0000-0000-0000-000000000106'::UUID
    WHEN 'BRATSK' THEN '00000000-0000-0000-0000-000000000107'::UUID
    WHEN 'CHITA' THEN '00000000-0000-0000-0000-000000000108'::UUID
END
WHERE region_selected = TRUE;

ALTER TABLE pending_shared_basket_imports ADD COLUMN location_id UUID REFERENCES geo_locations(id);

UPDATE pending_shared_basket_imports
SET location_id = CASE region_code
    WHEN 'MOSCOW' THEN '00000000-0000-0000-0000-000000000101'::UUID
    WHEN 'SAINT_PETERSBURG' THEN '00000000-0000-0000-0000-000000000102'::UUID
    WHEN 'EKATERINBURG' THEN '00000000-0000-0000-0000-000000000103'::UUID
    WHEN 'NOVOSIBIRSK' THEN '00000000-0000-0000-0000-000000000104'::UUID
    WHEN 'KRASNOYARSK' THEN '00000000-0000-0000-0000-000000000105'::UUID
    WHEN 'IRKUTSK' THEN '00000000-0000-0000-0000-000000000106'::UUID
    WHEN 'BRATSK' THEN '00000000-0000-0000-0000-000000000107'::UUID
    WHEN 'CHITA' THEN '00000000-0000-0000-0000-000000000108'::UUID
END;

ALTER TABLE pending_shared_basket_imports ALTER COLUMN location_id SET NOT NULL;

ALTER TABLE user_profiles
    DROP CONSTRAINT fk_user_profiles_region,
    DROP COLUMN region_code,
    DROP COLUMN region_selected;

ALTER TABLE pending_shared_basket_imports
    DROP CONSTRAINT fk_pending_shared_basket_region,
    DROP COLUMN region_code;

DROP TABLE marketplace_regions;

CREATE TABLE pending_city_selections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES user_profiles(id) ON DELETE CASCADE,
    candidates JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_city_selection_candidates CHECK (jsonb_typeof(candidates) = 'array'),
    CONSTRAINT ck_pending_city_selection_lifecycle CHECK (expires_at > created_at)
);

CREATE INDEX idx_pending_city_selections_expiry
    ON pending_city_selections (expires_at);
