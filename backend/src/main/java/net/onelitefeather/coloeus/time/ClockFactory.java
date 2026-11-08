package net.onelitefeather.coloeus.time;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * The one {@link Clock} of the service. Components take it as a constructor argument, so tests inject a fixed clock.
 */
@Factory
public class ClockFactory {

    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
