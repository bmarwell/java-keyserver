/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.it.extension.KeyserverAccess;
import io.github.bmarwell.keyserver.it.extension.KeyserverIntegrationTest;
import io.github.bmarwell.keyserver.it.support.TestPgpKeyGenerator;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.Test;

/// Integration test for the `POST /pks/add` HKP endpoint.
///
/// Uses the JAX-RS {@link Client} API (Apache CXF as runtime implementation) for
/// consistency with the rest of the application — the same API will be used in
/// future REST JSON endpoint tests.
///
/// A fresh RSA-2048 OpenPGP key is generated at test runtime via
/// {@link TestPgpKeyGenerator} — no static key file is checked into the repository.
///
/// Asserts:
/// 1. The server responds with {@code 202 Accepted}.
/// 2. A row appears in {@code verification_queue} for the generated email address.
///
/// This class does not declare {@link io.github.bmarwell.keyserver.it.extension.DatabaseSeed}
/// because the shared PostgreSQL instance starts empty at the beginning of the test session
/// and the test uses a generated key. Tests that need a pre-populated database or
/// deterministic cleanup should annotate their class with {@code @DatabaseSeed}.
@KeyserverIntegrationTest
class AddKeyIT {

    private static final String TEST_EMAIL = "it-generated@example.com";
    private static final String TEST_USER_ID = "IT Generated Key <" + TEST_EMAIL + ">";

    @Test
    void add_key_returns_202_and_enqueues_verification_entry(KeyserverAccess keyserver)
            throws IOException, PGPException {
        // given
        String armoredKey = TestPgpKeyGenerator.generateArmoredPublicKey(TEST_USER_ID);

        // when — JAX-RS Client: consistent with the application's JAX-RS stack
        try (Client client = ClientBuilder.newClient()) {
            Response response = client.target(keyserver.pksBaseUri())
                    .path("add")
                    .request()
                    .post(Entity.form(new Form("keytext", armoredKey)));

            // then — HTTP layer
            assertThat(response.getStatus())
                    .as("POST /pks/add should respond with 202 Accepted")
                    .isEqualTo(202);
        }

        // then — persistence layer: one row in verification_queue for the generated email
        long queuedCount = countVerificationQueueEntriesFor(keyserver, TEST_EMAIL);
        assertThat(queuedCount)
                .as("verification_queue should contain one entry for %s", TEST_EMAIL)
                .isGreaterThanOrEqualTo(1);
    }

    private static long countVerificationQueueEntriesFor(KeyserverAccess keyserver, String email) {
        String sql = "SELECT COUNT(*) FROM verification_queue WHERE uid_email = ?";
        try (Connection conn =
                        DriverManager.getConnection(keyserver.jdbcUrl(), keyserver.dbUser(), keyserver.dbPassword());
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot query verification_queue", ex);
        }
    }
}
