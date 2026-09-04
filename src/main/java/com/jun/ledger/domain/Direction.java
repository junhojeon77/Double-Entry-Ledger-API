package com.jun.ledger.domain;

/**
 * Which way money moved on a single posting line.
 *
 * WHAT IT DOES: carries the sign, so amounts never have to. Every amount in this
 * system is stored positive; this says whether that amount left an account (DEBIT)
 * or arrived in one (CREDIT).
 *
 * WHY AN ENUM RATHER THAN A SIGNED LONG: a signed amount plus a direction column
 * would encode the same fact twice, and two encodings of one fact can disagree.
 * With direction as the only carrier of sign there is nothing to keep in sync, and
 * the compiler rules out every value that isn't one of these two.
 *
 * SIGN CONVENTION — DEBIT is negative, CREDIT is positive. This matches the CASE
 * in V2__invariants.sql exactly, which is what makes the database's balance check
 * and this code agree. If you ever change one, change the other in the same commit.
 *
 * MIRRORS THE DATABASE: posting.direction has CHECK (direction IN ('DEBIT','CREDIT')),
 * so the constant names must stay spelled exactly like this.
 *
 * FUTURE: never grows a third constant. Anything that feels like a third direction
 * is a different kind of transaction, not a new sign.
 */
public enum Direction {
    DEBIT,
    CREDIT
}
