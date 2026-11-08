package net.onelitefeather.coloeus.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock that only moves when a test moves it. */
final class MutableClock extends Clock {

    static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    private Instant now;

    MutableClock() {
        this.now = START;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }

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
