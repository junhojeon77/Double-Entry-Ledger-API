package com.jun.ledger.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the rules Money enforces on its own.
 *
 * WHAT THIS FILE PROTECTS: the three decisions Money makes that nothing else will
 * catch — a currency code must be three characters, two amounts in different
 * currencies must not be addable, and arithmetic must throw rather than silently
 * wrap past Long.MAX_VALUE. Every one of those failures is silent in production:
 * a wrong balance does not crash, does not log, and does not page anyone. Tests are
 * the only detection there is.
 *
 * TWO KINDS OF TEST LIVE HERE, and they are not equally valuable:
 *   1. Rule tests — the four "...Throws" / "...Rejected" cases. These guard logic
 *      written in Money.java. Delete a guard there and one of these goes red.
 *   2. Record tests — testEquals, testHashCode, testToString, testCurrencyCode,
 *      testMinorUnits. These assert behaviour the Java compiler generates for every
 *      record. They cannot fail unless the record's components change, so they
 *      document the shape rather than protect a rule.
 *
 * ONE CAVEAT ON testToString: it asserts the exact generated format
 * ("Money[minorUnits=100, currencyCode=USD]"). Rename a component and it fails with
 * no behaviour change — the definition of a brittle test. Kept because it documents
 * the format, but do not treat a failure there as a real regression.
 *
 * NOT TESTED HERE: anything involving two accounts, balances, or persistence. Money
 * knows nothing about accounts — that is PostingEngineTest's job.
 *
 * SPEED: no Spring, no database, no container. The whole file runs in a few
 * milliseconds, which is what makes it cheap enough to run on every save.
 */
public class MoneyTest {

    // ---- Rule tests: these protect logic written in Money.java ----

    // in: Money(1000,"USD").plus(Money(1000,"CAD")) -> out: throws IllegalArgumentException
    @Test
    void addingDifferentCurrenciesThrows() {
        Money usd = new Money(1000, "USD");
        Money cad = new Money(1000, "CAD");
        assertThrows(IllegalArgumentException.class, () -> usd.plus(cad));
    }

    // in: Money(Long.MAX_VALUE,"USD").plus(Money(1,"USD")) -> out: throws ArithmeticException
    // This is Math.addExact earning its keep. Plain + would wrap silently and turn
    // the largest possible credit into a large debit.
    @Test
    void overflowThrowsInsteadOfWrapping() {
        Money max = new Money(Long.MAX_VALUE, "USD");
        assertThrows(ArithmeticException.class, () -> max.plus(new Money(1, "USD")));
    }

    // in: Money(Long.MIN_VALUE,"USD").negate() -> out: throws ArithmeticException
    // MIN_VALUE has no positive counterpart, so plain negation returns MIN_VALUE
    // again — still negative. Math.negateExact throws instead.
    @Test
    void negateOfMinValueThrowsInsteadOfWrapping() {
        Money min = new Money(Long.MIN_VALUE, "USD");
        assertThrows(ArithmeticException.class, () -> min.negate());
    }

    // in: new Money(1,"US") and new Money(1,null) -> out: both throw IllegalArgumentException
    @Test
    void currencyCodeMustBeThreeLetters() {
        assertThrows(IllegalArgumentException.class, () -> new Money(1, "US"));
        assertThrows(IllegalArgumentException.class, () -> new Money(1, null));
    }

    // in: Money(100,"USD") and Money(-100,"USD") -> out: true, false
    @Test
    void testIsPositive() {
        Money positiveMoney = new Money(100, "USD");
        Money negativeMoney = new Money(-100, "USD");
        assertTrue(positiveMoney.isPositive());
        assertFalse(negativeMoney.isPositive());
    }

    // in: Money(0,"USD").negate() -> out: Money(0,"USD")
    // Zero is its own negative; this pins that negate() has no special case for it.
    @Test
    void negateOfZeroIsZero() {
        Money zero = new Money(0, "USD");
        Money result = zero.negate();
        assertEquals(new Money(0, "USD"), result);
    }

    // in: Money(100,"USD").plus(Money(200,"USD")) -> out: Money(300,"USD")
    @Test
    void testPlus() {
        Money money1 = new Money(100, "USD");
        Money money2 = new Money(200, "USD");
        Money result = money1.plus(money2);
        assertEquals(new Money(300, "USD"), result);
    }

    // ---- Record tests: these document the shape, not a rule ----
    // Everything below asserts compiler-generated record behaviour. They cannot
    // fail unless the record's components change.

    // in: Money(100,"USD").currencyCode() -> out: "USD"
    @Test
    void testCurrencyCode() {
        Money money = new Money(100, "USD");
        assertEquals("USD", money.currencyCode());
    }

    // in: Money(100,"USD").minorUnits() -> out: 100L
    @Test
    void testMinorUnits() {
        Money money = new Money(100, "USD");
        assertEquals(100, money.minorUnits());
    }

    // in: two Money(100,"USD") -> out: equal
    // Value equality, not reference equality — this is why assertEquals can compare
    // whole Money objects elsewhere instead of comparing field by field.
    @Test
    void testEquals() {
        Money money1 = new Money(100, "USD");
        Money money2 = new Money(100, "USD");
        assertEquals(money1, money2);
    }

    // in: two Money(0,"USD") -> out: identical hashCode
    @Test
    void testHashCode() {
        Money money1 = new Money(0, "USD");
        Money money2 = new Money(0, "USD");
        assertEquals(money1.hashCode(), money2.hashCode());
    }

    // in: Money(100,"USD").toString() -> out: "Money[minorUnits=100, currencyCode=USD]"
    // Brittle: renaming a component breaks this with no behaviour change.
    @Test
    void testToString() {
        Money money = new Money(100, "USD");
        assertEquals("Money[minorUnits=100, currencyCode=USD]", money.toString());
    }
}
