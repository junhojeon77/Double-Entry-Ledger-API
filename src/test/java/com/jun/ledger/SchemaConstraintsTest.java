package com.jun.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the database defends itself, independently of any Java.
 *
 * WHY THIS FILE EXISTS: every guarantee in this project has two layers — one in
 * application code, one in the schema. PostingEngineTest covers the first. This
 * covers the second, and the second layer is the one that assumes the first will
 * eventually have a bug. Testing your own constraints is the step almost everyone
 * skips; without it you have constraints you *believe* work.
 *
 * WHY JdbcTemplate AND NOT JPA ENTITIES: nothing here tests object mapping. Entities
 * and repositories would be a lot of unmotivated code standing between the test and
 * the SQL under test. They arrive in Cycle 4, when TransferService needs them.
 *
 * THE ANNOTATION STACK, and why each line is load-bearing:
 *   @JdbcTest                             — slice with a DataSource and JdbcTemplate;
 *                                           no web layer, no JPA.
 *   @AutoConfigureTestDatabase(NONE)      — do NOT swap in an embedded database. Without
 *                                           it Boot replaces the container with H2 and
 *                                           every Postgres-specific constraint vanishes.
 *   @AutoConfigureDataSourceInitialization — runs Flyway. The @JdbcTest slice does NOT
 *                                           include it. Omit this and you get an empty
 *                                           database and "relation does not exist" on
 *                                           every single test.
 *   @Import(TestcontainersConfiguration)  — supplies the real Postgres 16.
 *   @Transactional(NOT_SUPPORTED)         — see below; this is the subtle one.
 *
 * WHY NOT_SUPPORTED: @JdbcTest is transactional and rolls back by default. But
 * posting_must_balance (V2) is DEFERRABLE INITIALLY DEFERRED — it fires only at
 * COMMIT. A rolled-back test never commits, so the trigger would never run, and a
 * test expecting a violation would pass having proved nothing. NOT_SUPPORTED turns
 * the wrapping transaction off so statements autocommit and the trigger really fires.
 *
 * TWO CONSEQUENCES OF THAT:
 *   1. Nothing rolls back, so @AfterEach truncates by hand.
 *   2. A lone posting can never be inserted — it autocommits, the deferred check sees
 *      an unbalanced transfer, and rejects it. Inserting a valid pair means putting
 *      both inserts in one transaction, which is what the TransactionTemplate is for.
 *
 * THE TWO TRIGGERS BEHAVE DIFFERENTLY — this is what catches people out:
 *   V2 posting_must_balance — DEFERRED, fires at COMMIT. Assert that the transaction
 *                             boundary throws, not that the insert throws.
 *   V3 posting_append_only  — BEFORE trigger, fires on the statement itself.
 *
 * ALWAYS PAIR A REJECTION WITH AN ACCEPTANCE: a test asserting
 * DataIntegrityViolationException also passes if you simply typo'd a column name.
 * Prove the valid case works before trusting the invalid one.
 *
 * FUTURE: the @Nested groups below are the Cycle 3 checklist, still to be filled in.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureDataSourceInitialization
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchemaConstraintsTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    /** Groups several statements into one transaction, so DEFERRED constraints fire at its commit. */
    TransactionTemplate tx;

    /** Re-seeded before every test: @AfterEach truncates, so ids captured once would go stale. */
    UUID srcId;
    UUID dstId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        seedAccounts();
    }

    // Nothing rolls back under NOT_SUPPORTED, so clean up explicitly.
    // CASCADE because posting and transfer hold foreign keys into account.
    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE posting, transfer, account CASCADE");
    }

    // in: nothing -> out: 3, the number of tables Flyway should have created
    // The canary, and the reason it runs first: it separates "my slice is
    // misconfigured" from "my constraint is wrong". If Flyway did not run, this
    // fails with a clear signal instead of leaving you debugging a CHECK test that
    // is failing for an entirely unrelated reason.
    @Test
    void migrationApplied() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'public'"
                        + " AND table_name IN ('account', 'transfer', 'posting')",
                Integer.class)).isEqualTo(3);
    }

    // ---- Cycle 3 checklist. Each group pairs every rejection with an acceptance. ----

    /** no_self_transfer; amount_minor > 0; unique idempotency_key. */
    @Nested
    class TransferConstraints { }

    /** balance_above_overdraft_limit — plus the control that a permitted overdraft succeeds. */
    @Nested
    class AccountConstraints { }

    /** amount_minor > 0; direction IN ('DEBIT','CREDIT'). */
    @Nested
    class PostingConstraints { }

    /** V3: UPDATE and DELETE on posting are rejected. BEFORE trigger — fires on the statement. */
    @Nested
    class AppendOnly { }

    /** V2: a balanced pair commits; an unbalanced pair and a lone posting are rejected at COMMIT. */
    @Nested
    class BalanceTrigger { }

    // ---- helpers, shared by every group above ----

    /** Two USD accounts holding $100.00 each; their ids land in srcId and dstId. */
    private void seedAccounts() {
        srcId = insertAccount("ACC-SRC-" + UUID.randomUUID(), "USD", 10_000L);
        dstId = insertAccount("ACC-DST-" + UUID.randomUUID(), "USD", 10_000L);
    }

    /** status, version, overdraft_limit_minor and created_at all take their schema defaults. */
    private UUID insertAccount(String accountNumber, String currency, long balanceMinor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO account (id, account_number, owner_name, currency, balance_minor)"
                + " VALUES (?, ?, ?, ?, ?)",
                id, accountNumber, "Test Owner", currency, balanceMinor);
        return id;
    }

    /** One PENDING transfer from srcId to dstId; its id is what a posting's transfer_id references. */
    private UUID seedTransfer() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transfer (id, idempotency_key, request_hash, status,"
                + " source_account_id, target_account_id, amount_minor, currency)"
                + " VALUES (?, ?, ?, 'PENDING', ?, ?, ?, 'USD')",
                id, "idem-" + id, "hash-" + id, srcId, dstId, 2500L);
        return id;
    }

    /**
     * One posting line. Call this inside tx.executeWithoutResult(...) when inserting a
     * pair — on its own it autocommits and the deferred balance trigger rejects it.
     */
    private void insertPosting(UUID transferId, UUID accountId, String direction, long amountMinor) {
        jdbc.update("INSERT INTO posting (transfer_id, account_id, direction, amount_minor, currency)"
                + " VALUES (?, ?, ?, ?, 'USD')",
                transferId, accountId, direction, amountMinor);
    }

    private int countPostings(UUID transferId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting WHERE transfer_id = ?", Integer.class, transferId);
    }
}
