package net.onelitefeather.coloeus.ledger;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.Money;

/**
 * A validated transaction. Its postings sum to zero per currency; that is checked here, before the database
 * trigger would reject it.
 */
public record TransactionCommand(
        TransactionKind kind,
        List<Leg> legs,
        String reason,
        String actor,
        String externalReference,
        Map<String, String> metadata) {

    public TransactionCommand {
        legs = List.copyOf(legs);
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        requireBalanced(legs);
    }

    private static void requireBalanced(List<Leg> legs) {
        Map<String, Money> sums = new HashMap<>();
        for (Leg leg : legs) {
            MonetaryAmount amount = leg.amount();
            sums.merge(amount.getCurrency().getCurrencyCode(), Money.from(amount), Money::add);
        }
        sums.forEach((currency, total) -> {
            if (!total.isZero()) {
                throw ProblemException.of(ProblemCode.UNBALANCED,
                        "postings do not sum to zero in " + currency);
            }
        });
    }
}
