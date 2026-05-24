/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

        // Both null and blank op are invalid — HKP requires a non-empty op parameter.
        if (op == null || op.isBlank() || search == null || search.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required parameters: op and search")
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
