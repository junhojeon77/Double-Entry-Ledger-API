package com.jun.ledger.domain;

/**
 * An amount of money in minor units (cents), tagged with its currency.
 *
 * WHAT IT DOES: makes two whole classes of bug unrepresentable. Money is never
 * a {@code double} — 0.1 + 0.2 != 0.3 in binary floating point, and a ledger that
 * drifts by a cent is a ledger nobody trusts. Money is never a bare {@code long}
 * either, because a bare long lets you add CAD to USD and get a number back.
 * Both mistakes become exceptions here instead of silently wrong balances.
 *
 * WHY {@code addExact} / {@code negateExact}: plain {@code +} on a long wraps
 * silently past {@code Long.MAX_VALUE}, turning a huge credit into a huge debit.
 * The Exact variants throw instead. Overflow is not realistic with real balances;
 * it is very realistic with a bug or a hostile input, and this is the trust boundary.
 *
 * FUTURE: stays framework-free forever. No JPA annotations, no Spring — it is
 * embedded into entities as two plain columns (amount + currency). Everything in
 * this package must remain testable in milliseconds with no database.
 */

public record Money(long minorUnits, String currencyCode) {
    public Money{
        if (currencyCode == null || currencyCode.length() != 3) {
            throw new IllegalArgumentException("Currency code must be a ISO 4217 code");
        }
    }

    public Money plus(Money o) { same(o); return new Money(Math.addExact(minorUnits, o.minorUnits), currencyCode); }
    public Money negate() {return new Money(Math.negateExact(minorUnits), currencyCode);}
    public boolean isPositive() {return minorUnits > 0;}

    private void same(Money o) {
        if (!currencyCode.equals(o.currencyCode)) {
            throw new IllegalArgumentException("Currency codes must match");
        }
    }
}
