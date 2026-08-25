CREATE TABLE marketplace_regions (
    code VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    wb_destination BIGINT NOT NULL,
    wb_spp INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL,
    CONSTRAINT ck_marketplace_regions_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_marketplace_regions_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_marketplace_regions_destination_not_zero CHECK (wb_destination <> 0),
    CONSTRAINT ck_marketplace_regions_spp_non_negative CHECK (wb_spp >= 0),
    CONSTRAINT ck_marketplace_regions_sort_order_positive CHECK (sort_order > 0),
    CONSTRAINT uk_marketplace_regions_sort_order UNIQUE (sort_order)
);

INSERT INTO marketplace_regions (
    code, display_name, wb_destination, wb_spp, enabled, sort_order
) VALUES
    ('MOSCOW', 'Москва', 1259570991, 30, TRUE, 1),
    ('SAINT_PETERSBURG', 'Санкт-Петербург', -1123299, 30, TRUE, 2),
    ('EKATERINBURG', 'Екатеринбург', 123589409, 30, TRUE, 3),
    ('NOVOSIBIRSK', 'Новосибирск', -366519, 30, TRUE, 4),
    ('KRASNOYARSK', 'Красноярск', -5854093, 30, TRUE, 5),
    ('IRKUTSK', 'Иркутск', -5827722, 30, TRUE, 6),
    ('BRATSK', 'Братск', 123586041, 30, TRUE, 7),
    ('CHITA', 'Чита', -5551586, 30, TRUE, 8);

ALTER TABLE user_profiles
    ADD COLUMN region_code VARCHAR(32);

UPDATE user_profiles
SET region_code = 'MOSCOW';

ALTER TABLE user_profiles
    ALTER COLUMN region_code SET DEFAULT 'MOSCOW',
    ALTER COLUMN region_code SET NOT NULL,
    ADD CONSTRAINT fk_user_profiles_region
        FOREIGN KEY (region_code) REFERENCES marketplace_regions(code);

ALTER TABLE user_profiles
    DROP COLUMN city_name,
    DROP COLUMN dest,
    DROP COLUMN spp;

ALTER TABLE pending_shared_basket_imports
    ADD COLUMN region_code VARCHAR(32);

UPDATE pending_shared_basket_imports
SET region_code = 'MOSCOW';

ALTER TABLE pending_shared_basket_imports
    ALTER COLUMN region_code SET NOT NULL,
    ADD CONSTRAINT fk_pending_shared_basket_region
        FOREIGN KEY (region_code) REFERENCES marketplace_regions(code);
