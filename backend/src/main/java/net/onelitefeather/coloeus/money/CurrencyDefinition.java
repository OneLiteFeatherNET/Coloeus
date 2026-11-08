package net.onelitefeather.coloeus.money;

/**
 * A currency as the money package needs it: its code and fixed number of fraction digits.
 */
public record CurrencyDefinition(String code, int fractionDigits) {
}
