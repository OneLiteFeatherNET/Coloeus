package net.onelitefeather.coloeus.paper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** A clock that only moves when a test moves it. */
final class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
