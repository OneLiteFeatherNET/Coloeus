package net.onelitefeather.coloeus.client;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import org.javamoney.moneta.CurrencyUnitBuilder;

/**
 * {@link ColoeusClient} over the JDK {@link HttpClient}. Every call carries the bearer token of the caller's app
 * registration; every write carries an {@code Idempotency-Key}. A write whose first attempt times out, fails to connect
 * or receives a 5xx answer is retried once when the rest of its budget covers another attempt.
 */
public final class HttpColoeusClient implements ColoeusClient {

    /** Per attempt timeout of a write, and the least time left in a budget that still allows a retry. */
    private static final Duration ATTEMPT_TIMEOUT = DEFAULT_TIMEOUT;

    private final String root;
    private final HttpClient http;
    private final TokenProvider tokens;
    private final LongSupplier ticker;
    private final Duration attemptTimeout;
    private final Map<String, CurrencyUnit> units = new ConcurrentHashMap<>();

    /**
     * @param config the connection settings
     * @param clock  the time source for token expiry
     * @return a client that talks to the configured server with the default timeouts
     */
    public static HttpColoeusClient create(ColoeusClientConfig config, Clock clock) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(ATTEMPT_TIMEOUT)
                .build();
        return new HttpColoeusClient(config, config.tokenEndpoint(), http, clock, System::nanoTime, ATTEMPT_TIMEOUT);
    }

    /**
     * @param ticker monotonic time in nanoseconds, used to measure how much of a write budget is spent; the
     *               {@link Clock} is for token expiry only
     */
    HttpColoeusClient(ColoeusClientConfig config, URI tokenEndpoint, HttpClient http, Clock clock,
                      LongSupplier ticker, Duration attemptTimeout) {
        String base = config.baseUri().toString();
        this.root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.http = http;
        this.tokens = new TokenProvider(config, tokenEndpoint, http, clock);
        this.ticker = ticker;
        this.attemptTimeout = attemptTimeout;
    }

    @Override
    public List<CurrencyDefinition> currencies(Duration timeout) {
        List<CurrencyDefinition> currencies = ColoeusJson.currencies(read("/v1/currencies", timeout));
        currencies.forEach(currency -> units.put(currency.code(), currency.unit()));
        return currencies;
    }

    @Override
    public UUID playerByName(String name, Duration timeout) {
        String path = "/v1/players/by-name/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ColoeusJson.uuid(ColoeusJson.object(read(path, timeout)), "uuid");
    }

    @Override
    public void reportName(UUID uuid, String name, String idempotencyKey, Duration budget) {
        write("PUT", "/v1/players/" + uuid + "/name", ColoeusJson.nameBody(name), idempotencyKey, budget);
    }

    @Override
    public PlayerAccount playerAccount(UUID uuid, Duration timeout) {
        return ColoeusJson.playerAccount(read("/v1/accounts/player/" + uuid, timeout), this::unit);
    }

    @Override
    public List<MonetaryAmount> balances(UUID accountId, Duration timeout) {
        JsonObject answer = ColoeusJson.object(read("/v1/accounts/" + accountId + "/balances", timeout));
        return ColoeusJson.amounts(answer.getAsJsonArray("balances"), this::unit);
    }

    @Override
    public TransactionResult transact(Transaction transaction, String idempotencyKey, Duration budget) {
        String answer = write("POST", "/v1/transactions", ColoeusJson.transactionBody(transaction),
                idempotencyKey, budget);
        return ColoeusJson.transactionResult(answer, this::unit);
    }

    private String read(String path, Duration timeout) {
        return attempt("GET", path, null, null, timeout);
    }

    /**
     * Sends a write once, and once more when the first attempt was unavailable and the budget still covers a full
     * attempt. Both attempts carry the same key, so the server books the transaction at most once.
     */
    private String write(String method, String path, String body, String key, Duration budget) {
        long start = ticker.getAsLong();
        try {
            return attempt(method, path, body, key, minimum(attemptTimeout, budget));
        } catch (ColoeusUnavailableException unavailable) {
            Duration remaining = budget.minus(Duration.ofNanos(ticker.getAsLong() - start));
            if (remaining.compareTo(attemptTimeout) < 0) {
                throw unavailable;
            }
            return attempt(method, path, body, key, attemptTimeout);
        }
    }

    /**
     * One request with the token of the caller. The token acquisition and the request share the timeout.
     */
    private String attempt(String method, String path, String body, String key, Duration timeout) {
        long deadline = ticker.getAsLong() + timeout.toNanos();
        String token = tokens.token(timeout);
        Duration left = Duration.ofNanos(deadline - ticker.getAsLong());
        if (left.isZero() || left.isNegative()) {
            throw new ColoeusUnavailableException("no time left for " + method + " " + path);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(root + path))
                .timeout(left)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        HttpResponse<String> response = send(builder.build());
        if (response.statusCode() / 100 == 2) {
            return response.body();
        }
        throw failure(response.statusCode(), response.body(), token);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ColoeusUnavailableException("Coloeus did not answer " + request.method() + " "
                    + request.uri().getPath() + " in time", e);
        } catch (IOException e) {
            throw new ColoeusUnavailableException("Coloeus cannot be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ColoeusUnavailableException("call to Coloeus interrupted", e);
        }
    }

    /**
     * Maps an error answer by its problem code. Only a 5xx answer without a known code is unavailable.
     */
    private RuntimeException failure(int status, String body, String token) {
        Optional<ColoeusJson.Problem> problem = ColoeusJson.problem(body);
        if (problem.isEmpty()) {
            if (status / 100 == 5) {
                return new ColoeusUnavailableException("Coloeus answered HTTP " + status + " without a problem document");
            }
            return new ColoeusProblemException("UNKNOWN", status, "answer without a problem document");
        }
        String code = problem.get().code();
        String detail = problem.get().detail();
        return switch (code) {
            case "INSUFFICIENT_FUNDS" -> new InsufficientFundsException(status, detail);
            case "FORBIDDEN_GRANT" -> new ForbiddenGrantException(status, detail);
            case "PLAYER_UNKNOWN" -> new PlayerUnknownException(status, detail);
            case "NAME_LOOKUP_UNAVAILABLE" -> new NameLookupUnavailableException(status, detail);
            case "UNAUTHORIZED" -> {
                tokens.rejected(token);
                yield new AuthenticationException(status, detail);
            }
            default -> status / 100 == 5
                    ? new ColoeusUnavailableException(code + " (HTTP " + status + "): " + detail)
                    : new ColoeusProblemException(code, status, detail);
        };
    }

    private CurrencyUnit unit(String code) {
        return units.computeIfAbsent(code, unknown -> CurrencyUnitBuilder.of(unknown, "coloeus")
                .setDefaultFractionDigits(2)
                .build());
    }

    private static Duration minimum(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
