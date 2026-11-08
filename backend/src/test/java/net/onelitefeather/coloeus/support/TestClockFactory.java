package net.onelitefeather.coloeus.support;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import java.time.Instant;
import net.onelitefeather.coloeus.time.ClockFactory;

/**
 * Replaces the system clock of the service with a {@link MutableClock} in the test context.
 */
@Factory
@Requires(env = Environment.TEST)
public class TestClockFactory {

    public static final Instant START = Instant.parse("2026-11-07T10:00:00Z");

    @Singleton
    @Replaces(factory = ClockFactory.class)
    public MutableClock clock() {
        return new MutableClock(START);
    }
}
