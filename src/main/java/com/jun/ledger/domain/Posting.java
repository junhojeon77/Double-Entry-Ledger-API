package com.jun.ledger.domain;

import java.util.UUID;

/**
 * One line of the ledger: a fact that money moved on one account.
 *
 * WHAT IT DOES: records that {@code amount} moved {@code direction} on
 * {@code accountId}, as part of the transfer {@code transferId}. Postings always
 * come in balanced pairs — see {@link PostingEngine}. A single posting on its own
 * is never valid.
 *
 * TRANSFER vs POSTING — the distinction the whole schema rests on. A transfer is an
 * *intent* ("move $25 from A to B"); it can be rejected and it has a status. A
 * posting is a *fact*; it happened, and it never changes. That is why they are
 * separate tables: a failed transfer leaves a row explaining why, with no postings
 * against it, so rejections stay auditable.
 *
 * WHY IT HOLDS Money, NOT A BARE long: currency stays attached to the number.
 * A {@code long amountMinor} plus a currency stored somewhere else is how you end
 * up adding CAD to USD and getting a plausible-looking answer.
 *
 * IMMUTABLE BY CONSTRUCTION: a record has no setters, and V3__immutable.sql rejects
 * UPDATE and DELETE on the posting table. Two layers, same rule. Corrections are
 * made by posting a compensating reversal, never by editing history.
 *
 * FUTURE: this stays framework-free. In Cycle 4 a separate JPA entity is mapped to
 * the posting table and built from these; this record never gains an annotation.
 */
public record Posting(UUID transferId, UUID accountId, Money amount, Direction direction) {
}
