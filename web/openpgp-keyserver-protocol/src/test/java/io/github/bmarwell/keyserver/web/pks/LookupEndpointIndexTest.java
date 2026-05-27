/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.KeyIndexResult;
import io.github.bmarwell.keyserver.application.api.KeyRepositoryService;
import io.github.bmarwell.keyserver.application.api.UidIndexEntry;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LookupEndpointIndexTest {

    private static final String FINGERPRINT = "A1B2C3D4E5F60708091011121314151617181920A1B2";
    private static final OffsetDateTime CREATION = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String UID_RAW = "Alice <alice@example.com>";

    private LookupEndpoint endpoint;
    private FakeKeyRepositoryService fakeService;

    @BeforeEach
    void setUp() {
        this.fakeService = new FakeKeyRepositoryService();
        this.endpoint = new LookupEndpoint();
        this.endpoint.setKeyRepositoryService(this.fakeService);
        this.endpoint.setHkpIndexRenderer(new HkpIndexRenderer());
    }

    @Test
    void returnsNotFoundWhenNoKeyMatchesIndexSearch() {
        // given — the repository contains no keys matching the search
        this.fakeService.indexResults = List.of();

        // when — the client sends op=index with options=mr
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");

        // then — a 404 is returned so GnuPG knows no key was found
        assertThat(response.getStatus())
                .as("empty index results must yield 404 so HKP clients know no key was found")
                .isEqualTo(404);
    }

    @Test
    void rendersMachineReadableInfoLineWithKeyCount() {
        // given — one key with one verified UID in the repository
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key =
                new KeyIndexResult(FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — the client sends op=index&options=mr (machine-readable)
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — the info header must contain the key count so GnuPG can allocate result structures
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body)
                .as("machine-readable response must start with info:1:<count> per HKP draft spec")
                .startsWith("info:1:1\n");
    }

    @Test
    void rendersMachineReadablePubLine() {
        // given — one RSA-2048 key (algorithm 1, bitStrength 2048) with a known creation time
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key =
                new KeyIndexResult(FINGERPRINT, 1, Optional.of(2048), CREATION, Optional.empty(), false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — pub: line must carry fingerprint, algorithm, keylen, epoch seconds, expiry (empty), and flags
        // The format is required by GnuPG for trust lookups: missing fields break key import
        assertThat(body)
                .as("pub line must contain fingerprint and algorithm for GnuPG to import the key")
                .contains("pub:" + FINGERPRINT + ":1:2048:" + CREATION.toEpochSecond() + "::\n");
    }

    @Test
    void rendersMachineReadableUidLinePercentEncoded() {
        // given — a UID string containing characters that need percent-encoding ('<', '>', ' ')
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key =
                new KeyIndexResult(FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — UID must be percent-encoded so clients can safely parse the colon-delimited format
        assertThat(body)
                .as("UID must be percent-encoded because colons and special chars would break the field delimiter")
                .contains("uid:Alice+%3Calice%40example.com%3E:");
    }

    @Test
    void setsFlagRForRevokedKey() {
        // given — a revoked key without expiration
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key =
                new KeyIndexResult(FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), true, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — the pub: flags field must contain 'r' so HKP clients display it as revoked
        // format: pub:<fingerprint>:<algo>:<keylen>:<ctime>:<exptime>:<flags>
        assertThat(body)
                .as("revoked key must have 'r' in flags so HKP clients display it as revoked")
                .contains("pub:" + FINGERPRINT + ":22::" + CREATION.toEpochSecond() + "::r\n");
    }

    @Test
    void setsFlagEForExpiredKey() {
        // given — a key whose expiration time is clearly in the past (year 2000)
        OffsetDateTime pastExpiry = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.of(pastExpiry), false);
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.of(pastExpiry), false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — the pub: flags field must contain 'e' so GnuPG knows to mark the key as expired
        assertThat(body)
                .as("expired key must have 'e' in flags so GnuPG does not attempt to use it for encryption")
                .contains(":e\n");
    }

    @Test
    void rendersHtmlWhenOptionsMrAbsent() {
        // given — one key in the repository, no options=mr in the request
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key =
                new KeyIndexResult(FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client sends op=index without options=mr (browser request)
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, null);
        String body = (String) response.getEntity();

        // then — response must be HTML so it can be displayed in a browser for human inspection
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("browser requests (no options=mr) must receive HTML for human readability")
                .startsWith("text/html");
        assertThat(body)
                .as("HTML body must contain the fingerprint so users can identify the key")
                .contains(FINGERPRINT);
    }

    // ---------------------------------------------------------------------------
    // Test double — avoids CDI/Mockito overhead; keeps the test focused on the
    // endpoint's routing and response-building logic only
    // ---------------------------------------------------------------------------

    private static final class FakeKeyRepositoryService implements KeyRepositoryService {

        List<KeyIndexResult> indexResults = List.of();

        @Override
        public Optional<String> getArmoredKeyBySearch(String search, boolean exactMatch) {
            return Optional.empty();
        }

        @Override
        public List<KeyIndexResult> searchForIndex(String search, boolean exactMatch) {
            return this.indexResults;
        }

        @Override
        public void getKeyByRepoAndKeyId(
                io.github.bmarwell.keyserver.common.ids.RepositoryName repoName,
                io.github.bmarwell.keyserver.common.ids.KeyId keyId) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public Optional<io.github.bmarwell.keyserver.common.ids.PgpPublicKey> getKeyByKeyId(
                io.github.bmarwell.keyserver.common.ids.KeyId keyId) {
            return Optional.empty();
        }
    }
}
