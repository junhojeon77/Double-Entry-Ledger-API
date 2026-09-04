package com.jun.ledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Supplies a real PostgreSQL for tests, started in Docker and thrown away after.
 *
 * WHY A REAL POSTGRES AND NOT H2: H2 does not reproduce Postgres row locking,
 * deferred constraints, or trigger behaviour. This project's guarantees live in
 * CHECK constraints and triggers, so a test suite on H2 would pass while the
 * concurrency and immutability bugs shipped. Testcontainers or nothing.
 *
 * THE TWO ANNOTATIONS ARE A PAIR:
 *   @Bean             — creates the container. Without it Spring never calls this
 *                       method, no container starts, and nothing below happens.
 *   @ServiceConnection — rewrites spring.datasource.* to point at whatever random
 *                       port the container came up on.
 * Drop @Bean and the failure reads "Connection to localhost:5432 refused", because
 * Spring quietly falls back to application.yml. That looks like a Docker or
 * credentials problem and will send you hunting in the wrong place — check this
 * annotation pair first whenever a container test can't connect.
 *
 * USED BY: any test that @Imports it — LedgerApplicationTests, SchemaConstraintsTest,
 * and every integration test from Cycle 3 on. Importing it in one place means one
 * container definition to keep in step with compose.yaml.
 *
 * FUTURE: as more integration tests appear, consider making the container static so
 * a single instance is reused across test classes instead of one per class.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
