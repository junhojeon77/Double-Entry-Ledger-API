package com.jun.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke test: does the whole application actually start?
 *
 * WHAT contextLoads() PROVES — far more than the empty body suggests. Booting the
 * full context means every bean resolved and every dependency was satisfiable, the
 * datasource connected to a real Postgres 16 in Docker, and Flyway applied V1, V2
 * and V3 cleanly in order. An empty method body is the entire test: if any of that
 * fails, the context never loads and the test errors.
 *
 * WHY IT IS THE FIRST THING TO RUN when something breaks: it separates "my wiring
 * is broken" from "my logic is wrong". If this fails, no other integration test is
 * worth reading yet.
 *
 * COST: seconds, not milliseconds — it starts a container and a full Spring context.
 * That is the trade for what it covers. The pure domain tests stay fast precisely so
 * this one can afford to be slow.
 *
 * FUTURE: stays a one-liner. Real behaviour goes in focused slices — SchemaConstraintsTest
 * for the database, @WebMvcTest for controllers in Cycle 7 — not here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerApplicationTests {

	// in: nothing -> out: passes if the Spring context starts and Flyway migrates
	@Test
	void contextLoads() {
	}

}
