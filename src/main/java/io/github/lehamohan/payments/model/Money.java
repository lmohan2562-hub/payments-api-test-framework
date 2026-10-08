package io.github.lehamohan.payments.model;

/**
 * Monetary amount in minor units (cents) to avoid floating point rounding.
 *
 * @param value    amount in minor units, e.g. 1999 = 19.99
 * @param currency ISO-4217 alpha code, e.g. USD
 */
public record Money(Long value, String currency) {

    public static Money usd(long cents) {
        return new Money(cents, "USD");
    }
}
