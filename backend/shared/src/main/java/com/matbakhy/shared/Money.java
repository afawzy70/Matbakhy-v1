package com.matbakhy.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount in integer minor units of an ISO 4217 currency. Arithmetic is checked: overflow and
 * currency mixing fail instead of producing a wrong total. No floating point is used anywhere.
 */
public record Money(long amountMinor, String currency) {

    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    public static final String EGP = "EGP";

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (!ISO_CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 alphabetic code");
        }
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(int quantity) {
        return new Money(Math.multiplyExact(amountMinor, (long) quantity), currency);
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
