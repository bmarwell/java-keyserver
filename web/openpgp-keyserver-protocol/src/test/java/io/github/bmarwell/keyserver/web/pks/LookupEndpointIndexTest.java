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
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, false, List.of(uid));
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
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 1, Optional.of(2048), CREATION, Optional.empty(), false, false, List.of(uid));
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
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — UID must be percent-encoded using %20 for spaces (RFC 3986), not + (form-encoding)
        // '+' is a legal literal character in UID strings and would be mis-decoded by strict clients
        assertThat(body)
                .as("UID must use %20 for spaces, not '+', because '+' is legal in UID strings and"
                        + " would be mis-decoded by strict RFC 3986 percent-encoding clients")
                .contains("uid:Alice%20%3Calice%40example.com%3E:");
    }

    @Test
    void setsFlagRForRevokedKey() {
        // given — a revoked key without expiration
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), true, false, List.of(uid));
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
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.of(pastExpiry), false, false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — the pub: flags field (not just the uid: flags) must contain 'e'
        // format: pub:<fingerprint>:<algo>:<keylen>:<ctime>:<exptime>:<flags>
        // Asserting the full pub: line ensures we test the key-level flag specifically,
        // not just the uid: line which would also show 'e' for the same expiry
        assertThat(body)
                .as("expired key must have 'e' in flags on the pub: line so GnuPG knows not to use it for encryption")
                .contains("pub:" + FINGERPRINT + ":22::" + CREATION.toEpochSecond() + ":" + pastExpiry.toEpochSecond()
                        + ":e\n");
    }

    @Test
    void rendersHtmlWhenOptionsMrAbsent() {
        // given — one key in the repository, no options=mr in the request
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, false, List.of(uid));
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
        assertThat(body)
                .as("HTML must include DOCTYPE and charset meta so browsers render it in standards mode with UTF-8")
                .startsWith("<!DOCTYPE html>");
        assertThat(body).contains("<meta charset=\"utf-8\">");
    }

    @Test
    void recognisesMrTokenAmongCommaDelimitedOptions() {
        // given — options contains 'mr' alongside another token
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, false, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client sends options=nm,mr (comma-separated list)
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "nm,mr");
        String body = (String) response.getEntity();

        // then — machine-readable format must be returned because 'mr' is in the options list
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body)
                .as("'mr' token in a comma-separated options list must trigger machine-readable output")
                .startsWith("info:1:");
    }

    @Test
    void setsFlagDForDisabledKey() {
        // given — a key that has been disabled on this keyserver (not OpenPGP-revoked)
        UidIndexEntry uid = new UidIndexEntry(UID_RAW, Optional.of(CREATION), Optional.empty(), false);
        KeyIndexResult key = new KeyIndexResult(
                FINGERPRINT, 22, Optional.empty(), CREATION, Optional.empty(), false, true, List.of(uid));
        this.fakeService.indexResults = List.of(key);

        // when — client requests machine-readable index
        Response response = this.endpoint.doLookup("index", "alice@example.com", null, "mr");
        String body = (String) response.getEntity();

        // then — the pub: flags field must contain 'd' (not 'r') since the key is disabled, not revoked
        // format: pub:<fingerprint>:<algo>:<keylen>:<ctime>:<exptime>:<flags>
        assertThat(body)
                .as("keyserver-disabled key must have 'd' in pub: flags; 'r' is reserved for OpenPGP revocation")
                .contains("pub:" + FINGERPRINT + ":22::" + CREATION.toEpochSecond() + "::d\n");
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
