/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.it.extension.KeyserverContainerExtension;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/// Integration test for the `POST /pks/add` HKP endpoint.
///
/// Starts a real Open Liberty container (with both WARs deployed) and a real
/// PostgreSQL container via {@link KeyserverContainerExtension}.
///
/// The test submits a real PGP public key and asserts:
///
/// 1. The server responds with `202 Accepted`.
/// 2. A row appears in `verification_queue` for the expected email address.
@ExtendWith(KeyserverContainerExtension.class)
class AddKeyIT {

    private static final String TEST_KEY_RESOURCE = "/pgp/test-key-with-email.asc";
    private static final String EXPECTED_EMAIL = "testkey@example.com";

    @Test
    void add_key_returns_202_and_enqueues_verification_entry() throws IOException, InterruptedException {
        // given
        String armoredKey = readClasspathResource(TEST_KEY_RESOURCE);
        String formBody = "keytext=" + URLEncoder.encode(armoredKey, StandardCharsets.UTF_8);

        URI endpoint = KeyserverContainerExtension.pksBaseUri().resolve("/pks/add");

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

        // then — persistence layer: one row in verification_queue for the expected email
        long queuedCount = countVerificationQueueEntriesFor(EXPECTED_EMAIL);
        assertThat(queuedCount)
                .as("verification_queue should contain one entry for %s", EXPECTED_EMAIL)
                .isGreaterThanOrEqualTo(1);
    }

    private static long countVerificationQueueEntriesFor(String email) {
        String sql = "SELECT COUNT(*) FROM verification_queue WHERE email = '" + email + "'";
        try (Connection conn = DriverManager.getConnection(
                        KeyserverContainerExtension.jdbcUrl(),
                        KeyserverContainerExtension.dbUser(),
                        KeyserverContainerExtension.dbPassword());
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot query verification_queue", ex);
        }
    }

    private static String readClasspathResource(String path) throws IOException {
        try (InputStream in = AddKeyIT.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
