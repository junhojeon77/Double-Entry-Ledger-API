package com.jun.ledger.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PostingEngineTest {

    private static final UUID TRANSFER = UUID.randomUUID();
    private static final AccountRef SOURCE = new AccountRef(UUID.randomUUID(), "USD");
    private static final AccountRef TARGET = new AccountRef(UUID.randomUUID(), "USD");
    private static final Money USD_10 = new Money(1000, "USD");


    // Makes a PostingEngine.post() call and asserts that the rsults is two lists of postings.
    @Test
    void transferProducesTwoPostings() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertEquals(2, postings.size());
    }

    @Test
    void sourceIsDebitedAndTargetIsCredited() {
        List<Posting> postings = PostingEngine.post(TRANSFER,SOURCE, TARGET, USD_10);
        Posting sourcePosting = postings.stream().filter(p -> p.accountId().equals(SOURCE.id())).findFirst().orElseThrow();
        Posting targetPosting = postings.stream().filter(p -> p.accountId().equals(TARGET.id())).findFirst().orElseThrow();
        assertEquals(Direction.DEBIT, sourcePosting.direction());
        assertEquals(Direction.CREDIT, targetPosting.direction());
    }

    @Test
    void postingsSumToZero() {
        List<Posting> postings = 
        PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        
        long total = postings.stream()
                .mapToLong(p -> p.direction() == Direction.DEBIT 
                ? -p.amount().minorUnits()
                : p.amount().minorUnits())
                .sum();
        assertEquals(0, total);
    }

    @Test
    void bothPostingShareTheTransferId() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertTrue(postings.stream().allMatch(p
            -> TRANSFER.equals(p.transferId())
        ));
    }

    @Test
    void postingsCarryTheRequestedAmount() {
        List<Posting> postings = PostingEngine.post(TRANSFER, SOURCE, TARGET, USD_10);
        assertTrue(postings.stream().allMatch(p 
            -> USD_10.equals(p.amount())
        ));
    }

    @Test
    void zeroAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.post(TRANSFER, SOURCE,
                TARGET, new Money(0, "USD")));
    }

    @Test
    void negativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.post(TRANSFER, SOURCE,
                TARGET, new Money(-100, "USD")));
                
    }

    @Test
    void selfTransferIsRejected() {
       assertThrows(IllegalArgumentException.class, () -> PostingEngine.post(TRANSFER, SOURCE,
                SOURCE, new Money(100, "USD")));
    }

    @Test
    void mismatchedAccountCurrenciesAreRejected() {
        AccountRef source = new AccountRef(UUID.randomUUID(), "USD");
        AccountRef target = new AccountRef(UUID.randomUUID(), "CAD");
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.post(TRANSFER, source,
                target, new Money(100, "USD")));
    }

    @Test
    void amountCurrentMustMatchTheAccounts() {
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.post(TRANSFER, SOURCE,
                TARGET, new Money(100, "CAD")));
    }
}
