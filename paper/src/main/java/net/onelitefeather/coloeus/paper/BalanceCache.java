package net.onelitefeather.coloeus.paper;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.money.MonetaryAmount;

/**
 * The balances of players in the default currency and the names of players, kept in this server's memory. It is not
 * authoritative: losing it costs a fetch, never money. Entries are stamped with the injected clock.
 */
final class BalanceCache {

    /**
     * @param balance   the balance in the default currency
     * @param updatedAt when the entry was stored
     */
    record Entry(MonetaryAmount balance, Instant updatedAt) {
    }

    private final Map<UUID, Entry> balances = new ConcurrentHashMap<>();
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private final Clock clock;

    BalanceCache(Clock clock) {
        this.clock = clock;
    }

    Optional<Entry> get(UUID player) {
        return Optional.ofNullable(balances.get(player));
    }

    void put(UUID player, MonetaryAmount balance) {
        balances.put(player, new Entry(balance, clock.instant()));
    }

    void evict(UUID player) {
        balances.remove(player);
    }

    void remember(String name, UUID player) {
        names.put(key(name), player);
    }

    /** @return the UUID that joined or was looked up under this name; names are compared without case */
    Optional<UUID> uuidOf(String name) {
        return Optional.ofNullable(names.get(key(name)));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
