package com.jun.ledger.domain;

import java.util.UUID;

/**
 * The minimum PostingEngine needs to know about an account: who it is, and what
 * currency it holds.
 *
 * WHAT IT DOES: lets the accounting rules run without a database. The engine has to
 * compare the two accounts' currencies and stamp their ids onto postings — that is
 * all it needs, so that is all this carries.
 *
 * WHAT IT DELIBERATELY OMITS: balance and status. Not an oversight. A balance is
 * only meaningful once a row is locked inside a transaction, so "can this account
 * afford it" is a TransferService question in Cycle 4, not an engine question.
 * Keeping balance out of here is what stops the pure layer from quietly growing a
 * rule it cannot actually enforce.
 *
 * FUTURE: temporary, and meant to be deleted. Cycle 4 introduces the real
 * {@code Account} @Entity with its @Version optimistic lock; at that point the
 * service reads an Account and hands the engine one of these, or this record is
 * dropped in favour of passing the id and currency directly. Do not add fields to
 * it in the meantime — every field added here is one more reason it survives past
 * its usefulness.
 */
public record AccountRef(UUID id, String currencyCode) {
}
