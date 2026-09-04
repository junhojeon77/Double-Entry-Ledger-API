package com.jun.ledger.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MoneyTest {
    @Test
    void testCurrencyCode() {
        Money money = new Money(100, "USD");
        assertEquals("USD", money.currencyCode());
    }

    @Test
    void testEquals() {
        Money money1 = new Money(100, "USD");
        Money money2 = new Money(100, "USD");
        assertEquals(money1, money2);
    }

    @Test
    void testHashCode() {
        Money money1 = new Money(0, "USD");
        Money money2 = new Money(0, "USD");
        assertEquals(money1.hashCode(), money2.hashCode());

    }

    @Test
    void testIsPositive() {
        Money positiveMoney = new Money(100, "USD");
        Money negativeMoney = new Money(-100, "USD");
        assertTrue(positiveMoney.isPositive());
        assertFalse(negativeMoney.isPositive());
    }

    @Test
    void testMinorUnits() {
        Money money = new Money(100, "USD");
        assertEquals(100, money.minorUnits());
    }

    @Test
    void negateOfZeroIsZero() {
        Money zero = new Money(0, "USD");
        // arrange
        Money result = zero.negate();
        // assert
        assertEquals(new Money(0, "USD"), result);
    }

    @Test
    void testPlus() {
        Money money1 = new Money(100, "USD");
        Money money2 = new Money(200, "USD");
        Money result = money1.plus(money2);
        assertEquals(new Money(300, "USD"), result);
    }

    @Test
    void testToString() {
        Money money = new Money(100, "USD");
        assertEquals("Money[minorUnits=100, currencyCode=USD]", money.toString());
    }
}
