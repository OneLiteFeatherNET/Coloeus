package net.onelitefeather.coloeus.player;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves player names to UUIDs (design decision 8). The register answers first; on a miss the Mojang profile API
 * answers, and its answer is stored. A failing Mojang lookup stores nothing.
 */
@Singleton
public class PlayerNameService {

    /** Minecraft user names: 1 to 16 ASCII letters, digits or underscores. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final JdbcTransactions transactions;
    private final PlayerNameRepository repository;
    private final MojangProfileClient mojang;
    private final Clock clock;

    public PlayerNameService(JdbcTransactions transactions, PlayerNameRepository repository,
                             MojangProfileClient mojang, Clock clock) {
        this.transactions = transactions;
        this.repository = repository;
        this.mojang = mojang;
        this.clock = clock;
    }

    /** Records that a player with this UUID joined under this name. */
    public PlayerNameView recordJoin(String uuidText, String name) {
        UUID uuid = parseUuid(uuidText);
        requireName(name);
        Instant now = clock.instant();
        return transactions.inTransaction(connection -> {
            repository.record(connection, uuid, name, NameSource.JOIN, now);
            return repository.find(connection, uuid, name)
                    .orElseThrow(() -> new IllegalStateException("the join was just recorded"));
        });
    }

    /**
     * The most recent holder of the name, with the names that UUID has held. On a miss the Mojang profile API is asked.
     *
     * @throws ProblemException {@link ProblemCode#PLAYER_UNKNOWN} or {@link ProblemCode#NAME_LOOKUP_UNAVAILABLE}
     */
    public PlayerLookup lookup(String name) {
        requireName(name);
        Optional<PlayerNameView> known = transactions.read(connection -> repository.newestHolder(connection, name));
        if (known.isPresent()) {
            return answer(known.get());
        }
        MojangProfile profile = findAtMojang(name)
                .orElseThrow(() -> ProblemException.of(ProblemCode.PLAYER_UNKNOWN,
                        "no player named " + name + " is known"));
        UUID uuid = profileUuid(profile);
        Instant now = clock.instant();
        return transactions.inTransaction(connection -> {
            repository.record(connection, uuid, profile.name(), NameSource.MOJANG, now);
            PlayerNameView holder = repository.newestHolder(connection, profile.name())
                    .orElseThrow(() -> new IllegalStateException("the name was just recorded"));
            return buildLookup(connection, holder);
        });
    }

    private PlayerLookup answer(PlayerNameView holder) {
        return transactions.read(connection -> buildLookup(connection, holder));
    }

    private PlayerLookup buildLookup(Connection connection, PlayerNameView holder) throws SQLException {
        List<NameEntry> history = repository.history(connection, holder.uuid());
        return new PlayerLookup(holder.uuid(), holder.name(), holder.lastSeen(), holder.source(), history);
    }

    /**
     * The profile of the name at Mojang: empty for 204 and 404, the profile for 200. Any other answer, and any failure
     * to get one, is unavailable. The client may hand a 404 back as an answer rather than throw it, so the status is
     * checked here as well as the exception.
     */
    private Optional<MojangProfile> findAtMojang(String name) {
        HttpResponse<MojangProfile> response;
        try {
            response = mojang.profile(name);
        } catch (HttpClientException e) {
            throw unavailable(name, e);
        }
        int status = response.getStatus().getCode();
        if (status == HttpStatus.NOT_FOUND.getCode() || status == HttpStatus.NO_CONTENT.getCode()) {
            return Optional.empty();
        }
        if (status != HttpStatus.OK.getCode()) {
            throw unavailable(name, null);
        }
        return Optional.of(response.getBody(MojangProfile.class)
                .orElseThrow(() -> unavailable(name, null)));
    }

    private static UUID profileUuid(MojangProfile profile) {
        try {
            return profile.uuid();
        } catch (IllegalArgumentException e) {
            throw unavailable(profile.name(), e);
        }
    }

    private static ProblemException unavailable(String name, Throwable cause) {
        ProblemException problem = ProblemException.of(ProblemCode.NAME_LOOKUP_UNAVAILABLE,
                "the profile API did not answer for " + name + "; nothing was stored");
        if (cause != null) {
            problem.initCause(cause);
        }
        return problem;
    }

    static void requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID,
                    "name must be 1 to 16 letters, digits or underscores");
        }
    }

    static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, "uuid must be a UUID");
        }
    }
}
