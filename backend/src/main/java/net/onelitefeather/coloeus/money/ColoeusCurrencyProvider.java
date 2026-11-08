package net.onelitefeather.coloeus.money;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.money.CurrencyQuery;
import javax.money.CurrencyUnit;
import javax.money.spi.CurrencyProviderSpi;
import org.javamoney.moneta.CurrencyUnitBuilder;

/**
 * JSR 354 currency provider serving the currencies stored in the database, so that
 * {@code Monetary.getCurrency("COINS")} resolves inside the service.
 * <p>
 * The map is replaced as a whole by {@link #replaceAll(Collection)}; readers see either the old or the new set.
 * The service calls it at start-up and after every currency change.
 */
public final class ColoeusCurrencyProvider implements CurrencyProviderSpi {

    static final String PROVIDER_NAME = "coloeus";

    private static volatile Map<String, CurrencyUnit> currencies = Map.of();

    /** Replaces every currency served by this provider. */
    public static void replaceAll(Collection<CurrencyDefinition> definitions) {
        Map<String, CurrencyUnit> next = new LinkedHashMap<>();
        for (CurrencyDefinition definition : definitions) {
            next.put(definition.code(), CurrencyUnitBuilder.of(definition.code(), PROVIDER_NAME)
                    .setDefaultFractionDigits(definition.fractionDigits())
                    .build());
        }
        currencies = Map.copyOf(next);
    }

    /** The currency unit for a code, if the database defines it. */
    public static Optional<CurrencyUnit> find(String code) {
        return Optional.ofNullable(currencies.get(code));
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {
        if (!query.getCountries().isEmpty() || !query.getNumericCodes().isEmpty()) {
            return Set.of();
        }
        Map<String, CurrencyUnit> snapshot = currencies;
        Collection<String> codes = query.getCurrencyCodes();
        if (codes.isEmpty()) {
            return new HashSet<>(snapshot.values());
        }
        Set<CurrencyUnit> result = new HashSet<>();
        for (String code : codes) {
            CurrencyUnit unit = snapshot.get(code);
            if (unit != null) {
                result.add(unit);
            }
        }
        return result;
    }
}
