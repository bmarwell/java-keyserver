/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/// Integration-test skeleton for the AddKey flow.
///
/// ## What this class will do (once fully implemented)
///
/// 1. Start a PostgreSQL container via Testcontainers.
/// 2. Start an Open Liberty container (or use the Liberty Maven Plugin's
///    `liberty:test-start` goal) pre-loaded with the WAR artifact.
/// 3. Submit a PGP key via `POST /pks/add` with `keytext=<armored key>`.
/// 4. Assert the response is `202 Accepted`.
/// 5. Query the `verification_queue` table directly via JDBC to verify the
///    entry was persisted with the correct fingerprint and email.
///
/// ## Current state
///
/// The test is @Disabled because the Liberty container setup is not yet wired.
/// The PostgreSQL container is started to prove Testcontainers is on the
/// classpath and configured correctly.
@Testcontainers
class AddKeyIT {

    @Container
    @SuppressWarnings("resource") // managed by Testcontainers
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("keyserver")
            .withUsername("keyserver")
            .withPassword("keyserver");

    @Test
    void postgres_container_starts() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(POSTGRES.getJdbcUrl()).startsWith("jdbc:postgresql://");
    }

    @Disabled("Liberty container wiring not yet implemented — see integration-tests/README")
    @Test
    void add_key_returns_202_and_enqueues_entry() {
        // TODO: build HTTP client pointing at Liberty container
        // TODO: POST /pks/add with keytext=<test key>
        // TODO: assert 202 Accepted
        // TODO: assert one row in verification_queue with email = testkey@example.com
    }
}
