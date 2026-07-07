package com.priceradar.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TestClocks {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-01-15T10:00:00Z");

    private TestClocks() {
    }

    public static Clock fixedUtc() {
        return fixedUtc(DEFAULT_INSTANT);
    }

    public static Clock fixedUtc(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
