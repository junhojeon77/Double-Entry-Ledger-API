package com.jun.ledger.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the accounting rules: one transfer becomes exactly two balanced postings,
 * or it is refused.
 *
 * WHAT THIS FILE PROTECTS — the invariant the whole service exists to defend. If
 * the two postings ever fail to cancel out, money was created or destroyed. There
 * is no error message for that in production; the balance is simply wrong, and
 * stays wrong until a customer notices.
 *
 * THE TWO GROUPS BELOW:
 *   Shape tests (the first five) — describe what a valid transfer produces. They
 *   passed before any validation existed and still pass now, which makes them a
 *   regression net: change PostingEngine and they say immediately whether the books
 *   still balance.
 *
 *   Rejection tests (the last five) — each one forced exactly one guard into
 *   PostingEngine. Delete a guard there and precisely one of these goes red. That
 *   one-to-one mapping is what test-first buys; it is not available afterwards.
 *
 * THE MOST IMPORTANT TEST HERE is sourceIsDebitedAndTargetIsCredited. Every other
 * test would still pass with the two accounts swapped — that one pins which account
 * actually loses the money.
 *
 * WHY POSTINGS ARE LOOKED UP BY FILTER, NOT BY get(0)/get(1): list order is not a
 * promise this API makes. Indexing would couple these tests to an ordering that
 * could change during a harmless refactor, and a test that breaks without a
 * behaviour change is a bad test.
 *
 * NOT TESTED HERE, ON PURPOSE:
 *   - insufficient funds — needs a balance, which needs a locked row (Cycle 4)
 *   - closed or frozen accounts — account status is not the engine's business
 *   - idempotency (Cycle 5), persistence, HTTP status codes
 * If a test in this file ever needs a mock, something has been put in the wrong
 * place: a pure function has nothing to mock.
 *
 * SECOND LAYER: every rule below is also enforced by the database — see the CHECK
 * constraints in V1__ledger.sql and the balance trigger in V2__invariants.sql.
 *
 * SPEED: no Spring, no container. All ten run in well under a tenth of a second.
 */
public class PostingEngineTest {

    private static final UUID TRANSFER = UUID.randomUUID();
    private static final AccountRef SOURCE = new AccountRef(UUID.randomUUID(), "USD");
    private static final AccountRef TARGET = new AccountRef(UUID.randomUUID(), "USD");
    private static final Money USD_10 = new Money(1000, "USD");

    // ---- Shape tests: what a valid transfer produces ----

    // in: (TRANSFER, SOURCE, TARGET, Money(1000,"USD")) -> out: List<Posting> of size 2
    @Test
    void transferProducesTwoPostings() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertEquals(2, postings.size());
    }

    // in: (TRANSFER, SOURCE, TARGET, Money(1000,"USD")) -> out: SOURCE is DEBIT, TARGET is CREDIT
    // Pins direction of travel. Swap the accounts and this is the only test that notices.
    @Test
    void sourceIsDebitedAndTargetIsCredited() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        Posting sourcePosting = postings.stream()
                .filter(p -> p.accountId().equals(SOURCE.id())).findFirst().orElseThrow();
        Posting targetPosting = postings.stream()
                .filter(p -> p.accountId().equals(TARGET.id())).findFirst().orElseThrow();
        assertEquals(Direction.DEBIT, sourcePosting.direction());
        assertEquals(Direction.CREDIT, targetPosting.direction());
    }

    // in: (TRANSFER, SOURCE, TARGET, Money(1000,"USD")) -> out: signed amounts sum to 0L
    // Double-entry itself, as one assertion. The sign convention is written out
    // inline rather than hidden behind a helper, so the test states it out loud:
    // DEBIT negative, CREDIT positive — the same CASE as V2__invariants.sql.
    @Test
    void postingsSumToZero() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);

        long total = postings.stream()
                .mapToLong(p -> p.direction() == Direction.DEBIT
                        ? -p.amount().minorUnits()
                        :  p.amount().minorUnits())
                .sum();
        assertEquals(0, total);
    }

    // in: (TRANSFER, SOURCE, TARGET, Money(1000,"USD")) -> out: both postings carry TRANSFER
    // Without this, postingsSumToZero is weaker than it looks: two postings from
    // unrelated transfers also sum to zero. This is what ties the pair together,
    // and it is what the V2 trigger groups by.
    @Test
    void bothPostingShareTheTransferId() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertTrue(postings.stream().allMatch(p -> TRANSFER.equals(p.transferId())));
    }

    // in: (TRANSFER, SOURCE, TARGET, Money(1000,"USD")) -> out: both postings hold Money(1000,"USD")
    // Compares whole Money values, so amount and currency are both checked at once.
    @Test
    void postingsCarryTheRequestedAmount() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertTrue(postings.stream().allMatch(p -> USD_10.equals(p.amount())));
    }

    // ---- Rejection tests: each one forced a guard into PostingEngine ----

    // in: amount Money(0,"USD") -> out: throws IllegalArgumentException
    // Money constructs zero happily — refusing it is the engine's rule, not Money's.
    // A zero transfer is a no-op that pollutes the ledger.
    @Test
    void zeroAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PostingEngine.post(TRANSFER, SOURCE, TARGET, new Money(0, "USD")));
    }

    // in: amount Money(-100,"USD") -> out: throws IllegalArgumentException
    // A negative amount would silently reverse direction — money flowing from the target.
    @Test
    void negativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PostingEngine.post(TRANSFER, SOURCE, TARGET, new Money(-100, "USD")));
    }

    // in: SOURCE as both accounts -> out: throws IllegalArgumentException
    // Would produce a debit and a credit on one account netting to zero: a pair of
    // postings recording nothing. Second layer: V1's no_self_transfer constraint.
    @Test
    void selfTransferIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PostingEngine.post(TRANSFER, SOURCE, SOURCE, new Money(100, "USD")));
    }

    // in: source "USD", target "CAD", amount Money(100,"USD") -> out: throws IllegalArgumentException
    // Cross-currency movement needs an FX rate and a third posting for the spread.
    // Out of scope, so refuse it rather than treat CAD as USD.
    @Test
    void mismatchedAccountCurrenciesAreRejected() {
        AccountRef source = new AccountRef(UUID.randomUUID(), "USD");
        AccountRef target = new AccountRef(UUID.randomUUID(), "CAD");
        assertThrows(IllegalArgumentException.class,
                () -> PostingEngine.post(TRANSFER, source, target, new Money(100, "USD")));
    }

    // in: both accounts "USD", amount Money(100,"CAD") -> out: throws IllegalArgumentException
    // Distinct from the test above: the accounts agree with each other and still
    // disagree with the money being moved. Both checks are needed.
    @Test
    void amountCurrencyMustMatchTheAccounts() {
        assertThrows(IllegalArgumentException.class,
                () -> PostingEngine.post(TRANSFER, SOURCE, TARGET, new Money(100, "CAD")));
    }
}
