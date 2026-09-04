package com.jun.ledger;

import org.springframework.boot.SpringApplication;

/**
 * Runs the real application locally against a throwaway Testcontainers Postgres.
 *
 * WHAT IT DOES: starts LedgerApplication exactly as production would, but swaps in
 * the container from TestcontainersConfiguration instead of the datasource in
 * application.yml. Flyway runs against a database that did not exist a second ago.
 *
 * WHEN TO RUN THIS INSTEAD OF LedgerApplication: when you want a clean database
 * every time — checking that migrations apply from scratch, or poking at endpoints
 * without caring what data you leave behind. Run LedgerApplication when you want
 * the persistent compose.yaml database and data that survives a restart.
 *
 * IT LIVES IN src/test because it depends on test-scope Testcontainers classes; it
 * is never packaged into the jar. It is a developer convenience, not a test — no
 * @Test in here, and it does not run in CI.
 *
 * FUTURE: once V4 seeds dev accounts, this becomes the fastest way to get a usable
 * local environment from nothing.
 */
public class TestLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
