package com.jun.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot entry point. Run this to start the service against the database in
 * compose.yaml; run TestLedgerApplication instead to start it against a
 * throwaway Testcontainers Postgres.
 *
 * WHAT @SpringBootApplication DOES: three things at once — marks this as a config
 * class, turns on auto-configuration (which is what wires the datasource, Flyway,
 * JPA and web layers from the classpath), and component-scans **this package and
 * everything under it**.
 *
 * WHY THAT SCAN MATTERS HERE: com.jun.ledger.domain sits under this package, so
 * anything in it carrying a Spring annotation would be picked up as a bean. The
 * domain package staying framework-free is deliberate — it keeps the accounting
 * rules testable in milliseconds with no container. Nothing in domain/ should ever
 * be annotated, and if something there needs a dependency injected, it belongs in
 * a different package.
 *
 * FUTURE: stays this small. Configuration goes in dedicated @Configuration classes
 * (SecurityConfig, and so on), never here.
 */
@SpringBootApplication
public class LedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerApplication.class, args);
	}

}
