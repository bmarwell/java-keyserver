/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.it.extension.DatabaseSeed;
import io.github.bmarwell.keyserver.it.extension.KeyserverAccess;
import io.github.bmarwell.keyserver.it.extension.KeyserverIntegrationTest;
import io.github.bmarwell.keyserver.it.support.TestPgpKeyGenerator;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.Test;

/// Integration test for the `POST /pks/add` HKP endpoint.
///
/// A fresh RSA-2048 OpenPGP key is generated at test runtime via
/// {@link TestPgpKeyGenerator} — no static key file is checked into the
/// repository.
///
/// The test submits the key and asserts:
///
/// 1. The server responds with {@code 202 Accepted}.
/// 2. A row appears in {@code verification_queue} for the test email address.
///
/// {@link DatabaseSeed} resets the relevant tables after the class so that
/// parallel or sequential test classes start with a clean slate.
@KeyserverIntegrationTest
@DatabaseSeed
class AddKeyIT {

    private static final String TEST_EMAIL = "it-generated@example.com";
    private static final String TEST_USER_ID = "IT Generated Key <" + TEST_EMAIL + ">";

    @Test
    void add_key_returns_202_and_enqueues_verification_entry(KeyserverAccess keyserver)
            throws IOException, InterruptedException, PGPException {
        // given
        String armoredKey = TestPgpKeyGenerator.generateArmoredPublicKey(TEST_USER_ID);
        String formBody = "keytext=" + URLEncoder.encode(armoredKey, StandardCharsets.UTF_8);
        URI endpoint = keyserver.pksBaseUri().resolve("/pks/add");

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(formBody))
                .build();

        // when
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, BodyHandlers.ofString());
        }

        // then — HTTP layer
        assertThat(response.statusCode())
                .as("POST /pks/add should respond with 202 Accepted")
                .isEqualTo(202);

        // then — persistence layer: one row in verification_queue for the generated email
        long queuedCount = countVerificationQueueEntriesFor(keyserver, TEST_EMAIL);
        assertThat(queuedCount)
                .as("verification_queue should contain one entry for %s", TEST_EMAIL)
                .isGreaterThanOrEqualTo(1);
    }

    private static long countVerificationQueueEntriesFor(KeyserverAccess keyserver, String email) {
        String sql = "SELECT COUNT(*) FROM verification_queue WHERE email = '" + email + "'";
        try (Connection conn =
                        DriverManager.getConnection(keyserver.jdbcUrl(), keyserver.dbUser(), keyserver.dbPassword());
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot query verification_queue", ex);
        }
    }
}
