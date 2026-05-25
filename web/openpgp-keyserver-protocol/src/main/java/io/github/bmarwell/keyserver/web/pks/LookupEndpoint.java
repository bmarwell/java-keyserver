/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.KeyRepositoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/// HKP `/pks/lookup` endpoint.
///
/// Handles `op=get` (return ASCII-armored key block) and stubs `op=index`/`op=vindex`
/// (machine-readable listing — future work).
///
/// Only keys with at least one verified UID are returned.  Search by fingerprint
/// (`0x<hex>`), long/short key ID, email address, or UID substring.
@Tag(name = "OpenPGP Keyserver Protocol: Lookup")
@Path("lookup")
public class LookupEndpoint {

    @Inject
    KeyRepositoryService keyRepositoryService;

    @GET
    public Response doLookup(
            @QueryParam("op") String op, @QueryParam("search") String search, @QueryParam("exact") String exact) {

        // Both null/blank op and search are invalid — HKP requires both to be present.
        // Report exactly which parameter(s) are missing so the client can self-correct.
        boolean missingOp = op == null || op.isBlank();
        boolean missingSearch = search == null || search.isBlank();
        if (missingOp || missingSearch) {
            String missing = missingOp && missingSearch ? "op and search" : missingOp ? "op" : "search";
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required parameter(s): " + missing)
                    .type("text/plain")
                    .build();
        }

        boolean exactMatch = "on".equalsIgnoreCase(exact);

        if ("get".equalsIgnoreCase(op)) {
            return handleGet(search, exactMatch);
        }

        // op=index and op=vindex are future work
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity("op=" + op + " is not yet implemented")
                .type("text/plain")
                .build();
    }

    private Response handleGet(String search, boolean exactMatch) {
        Optional<String> armoredKey = keyRepositoryService.getArmoredKeyBySearch(search, exactMatch);
        if (armoredKey.isEmpty()) {
            // Return plain text 404 for consistency with other HKP error responses.
            // Throwing KeyNotFoundException would invoke the JSON exception mapper,
            // which mixes content types and confuses HKP clients expecting text/plain.
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No key found for the provided search term")
                    .type("text/plain")
                    .build();
        }
        return Response.ok(armoredKey.get())
                .type("application/pgp-keys; charset=us-ascii")
                .build();
    }

    // CDI-friendly setter for unit testing
    public void setKeyRepositoryService(KeyRepositoryService keyRepositoryService) {
        this.keyRepositoryService = keyRepositoryService;
    }
}
