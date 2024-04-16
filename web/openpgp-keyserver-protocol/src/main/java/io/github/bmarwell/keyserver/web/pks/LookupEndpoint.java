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
import io.github.bmarwell.keyserver.common.ids.KeyId;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Implementation of the OpenPGP Keyserver protocol.
 */
@Tag(name = "OpenPGP Keyserver Protocol: Lookup")
@Path("lookup")
public class LookupEndpoint {

    @Inject
    KeyRepositoryService keyRepositoryService;

    @GET
    public Response doLookup(@QueryParam("op") String op, @QueryParam("search") String search) {
        if (op.equals("get") && !search.isBlank()) {
            /*
             * The response to a successful "get" request is a HTTP document containing a keyring
             * as specified in OpenPGP [RFC4880], section 11.1,
             * and ASCII armored as specified in section 6.2.
             */
            Optional<PgpPublicKey> foundKey = this.keyRepositoryService.getKeyByKeyId(KeyId.fromString(search));
            if (foundKey.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .encoding(MediaType.TEXT_PLAIN)
                        .entity("Not Found")
                        .build();
            }

            // TODO: return armored-keys
            return Response.status(Response.Status.OK)
                    .encoding("application/pgp-keys; charset=us-ascii")
                    .build();
        }

        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    public KeyRepositoryService getRepositoryService() {
        return keyRepositoryService;
    }

    public void setRepositoryService(KeyRepositoryService keyRepositoryService) {
        this.keyRepositoryService = keyRepositoryService;
    }
}
