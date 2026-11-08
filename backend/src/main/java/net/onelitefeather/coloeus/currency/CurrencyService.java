package net.onelitefeather.coloeus.currency;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.money.ColoeusCurrencyProvider;
import net.onelitefeather.coloeus.money.CurrencyDefinition;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.persistence.UncheckedSqlException;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Currency administration. After every change the JSR 354 provider of this replica is reloaded from the database.
 */
@Singleton
public class CurrencyService {

    static final int MIN_FRACTION_DIGITS = 0;
    static final int MAX_FRACTION_DIGITS = 8;

    private static final Pattern CODE = Pattern.compile("[A-Z]{3,8}");
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_SYMBOL_LENGTH = 8;

    private final JdbcTransactions transactions;
    private final CurrencyRepository repository;
    private final Clock clock;
    private final Object reloadLock = new Object();

    public CurrencyService(JdbcTransactions transactions, CurrencyRepository repository, Clock clock) {
        this.transactions = transactions;
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        reloadProvider();
    }

    public Currency create(CreateCurrencyRequest request) {
        String code = requireCode(request.code());
        String nameSingular = requireText("nameSingular", request.nameSingular(), MAX_NAME_LENGTH);
        String namePlural = requireText("namePlural", request.namePlural(), MAX_NAME_LENGTH);
        String symbol = requireText("symbol", request.symbol(), MAX_SYMBOL_LENGTH);
        int fractionDigits = requireFractionDigits(request.fractionDigits());
        Instant now = clock.instant();
        Currency created;
        try {
            created = transactions.inTransaction(connection -> {
                repository.lockCurrencyWrites(connection);
                // The first currency is the default one; the lock keeps that decision race-free.
                boolean first = repository.count(connection) == 0;
                Currency currency = new Currency(code, nameSingular, namePlural, symbol, fractionDigits,
                        first, now, now);
                repository.insert(connection, currency);
                return currency;
            });
        } catch (UncheckedSqlException e) {
            if (e.isState(UncheckedSqlException.UNIQUE_VIOLATION)) {
                throw ProblemException.of(ProblemCode.CURRENCY_EXISTS, "currency " + code + " already exists");
            }
            throw e;
        }
        reloadProvider();
        return created;
    }

    public List<Currency> list() {
        return transactions.read(repository::findAll);
    }

    /**
     * The JSR 354 unit of a code served by this replica. A miss reloads the provider from the database once, so a
     * currency created by another replica resolves here too; only a code that is still missing stays unknown.
     */
    public Optional<CurrencyUnit> lookup(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Optional<CurrencyUnit> known = ColoeusCurrencyProvider.find(code);
        if (known.isPresent()) {
            return known;
        }
        reloadProvider();
        return ColoeusCurrencyProvider.find(code);
    }

    public Currency update(String code, UpdateCurrencyRequest request) {
        String nameSingular = requireText("nameSingular", request.nameSingular(), MAX_NAME_LENGTH);
        String namePlural = requireText("namePlural", request.namePlural(), MAX_NAME_LENGTH);
        String symbol = requireText("symbol", request.symbol(), MAX_SYMBOL_LENGTH);
        if (request.fractionDigits() != null) {
            requireFractionDigits(request.fractionDigits());
        }
        Instant now = clock.instant();
        Currency updated = transactions.inTransaction(connection -> {
            Currency current = repository.findByCode(connection, code, true)
                    .orElseThrow(() -> notFound(code));
            if (request.fractionDigits() != null && request.fractionDigits() != current.fractionDigits()) {
                throw ProblemException.of(ProblemCode.CURRENCY_SCALE_IMMUTABLE,
                        "currency " + code + " keeps " + current.fractionDigits() + " fraction digits");
            }
            repository.updateNames(connection, code, nameSingular, namePlural, symbol, now);
            return repository.findByCode(connection, code, false).orElseThrow(() -> notFound(code));
        });
        reloadProvider();
        return updated;
    }

    public Currency makeDefault(String code) {
        Instant now = clock.instant();
        Currency result = transactions.inTransaction(connection -> {
            repository.lockCurrencyWrites(connection);
            repository.findByCode(connection, code, true).orElseThrow(() -> notFound(code));
            repository.clearDefaultExcept(connection, code, now);
            repository.markDefault(connection, code, now);
            return repository.findByCode(connection, code, false).orElseThrow(() -> notFound(code));
        });
        reloadProvider();
        return result;
    }

    /**
     * Replaces this replica's JSR 354 currencies with the stored ones. Under one lock, so a reload always reads the
     * state of its own commit or a later one, and the last reload to finish holds the newest state.
     */
    void reloadProvider() {
        synchronized (reloadLock) {
            List<CurrencyDefinition> definitions = transactions.read(repository::findAll).stream()
                    .map(currency -> new CurrencyDefinition(currency.code(), currency.fractionDigits()))
                    .toList();
            ColoeusCurrencyProvider.replaceAll(definitions);
        }
    }

    private static ProblemException notFound(String code) {
        return ProblemException.of(ProblemCode.CURRENCY_NOT_FOUND, "currency " + code + " does not exist");
    }

    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID,
                    "code must be 3 to 8 upper case letters A to Z");
        }
        return code;
    }

    private static String requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, field + " must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID,
                    field + " must not be longer than " + maxLength + " characters");
        }
        return value;
    }

    private static int requireFractionDigits(Integer fractionDigits) {
        if (fractionDigits == null || fractionDigits < MIN_FRACTION_DIGITS || fractionDigits > MAX_FRACTION_DIGITS) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID,
                    "fractionDigits must be between " + MIN_FRACTION_DIGITS + " and " + MAX_FRACTION_DIGITS);
        }
        return fractionDigits;
    }
}
