/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Repositories")
@Path("repo")
@ApplicationScoped
public class RepositoryEndpoint {

    @GET
    public Response getListOfRepositoryNames() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }
}
