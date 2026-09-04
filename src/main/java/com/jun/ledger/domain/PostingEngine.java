package com.jun.ledger.domain;

import java.util.List;
import java.util.UUID;

/**
 * Turns one transfer into the balanced pair of postings that record it.
 *
 * WHAT IT DOES: the accounting rules, and nothing else. Given a transfer id, two
 * accounts and an amount, it returns exactly two postings — a DEBIT on the source
 * and a CREDIT on the target — or throws. There is no third outcome.
 *
 * WHY IT IS PURE: no Spring, no JPA, no clock, no database. A pure function can't
 * reach out mid-calculation, so the accounting rules are reviewable in one sitting
 * and testable in milliseconds. This is the class an auditor would read.
 *
 * WHAT IT DELIBERATELY DOES NOT KNOW: account balances (so no insufficient-funds
 * check — that needs a locked row and belongs in TransferService), account status,
 * idempotency keys, request hashes. Those are transfer-layer concerns. If this
 * class ever needs a dependency injected, something has been put in the wrong place.
 *
 * FUTURE: called by TransferService inside its @Transactional method in Cycle 4.
 * The postings it returns get persisted as LedgerEntry rows; this class never
 * learns that. Every rule below has a second layer in the database — see the
 * CHECK constraints in V1 and the balance trigger in V2.
 */
public final class PostingEngine {

    private PostingEngine() { } // static utility class

    public static List<Posting> post(UUID transferId, AccountRef source, AccountRef target, Money amount) {

        // A zero transfer is a no-op that pollutes the ledger; a negative one silently
        // reverses direction, moving money *from* the target. Second layer: V1's
        // CHECK (amount_minor > 0) on both transfer and posting.
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive, was " + amount.minorUnits());
        }

        // Debiting and crediting one account nets to zero — a pair of postings
        // recording nothing. Second layer: V1's no_self_transfer constraint.
        if (source.id().equals(target.id())) {
            throw new IllegalArgumentException("Cannot transfer to the same account: " + source.id());
        }

        // Cross-currency movement needs an FX rate and a third posting for the spread.
        // That is out of scope, so refuse it loudly rather than treat CAD as USD.
        if (!source.currencyCode().equals(target.currencyCode())) {
            throw new IllegalArgumentException(
                    "Account currencies must match: " + source.currencyCode() + " vs " + target.currencyCode());
        }

        // Distinct from the check above: both accounts may agree with each other and
        // still disagree with the amount being moved.
        if (!amount.currencyCode().equals(source.currencyCode())) {
            throw new IllegalArgumentException(
                    "Amount currency " + amount.currencyCode() + " does not match account currency "
                            + source.currencyCode());
        }

        // Immutable list of immutable records: these describe facts that already
        // happened, so nothing downstream may edit them.
        return List.of(
                new Posting(transferId, source.id(), amount, Direction.DEBIT),
                new Posting(transferId, target.id(), amount, Direction.CREDIT));
    }
}
