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
package io.github.bmarwell.keyserver.web.rest;

import io.github.bmarwell.keyserver.application.api.KeyRepositoryService;
import io.github.bmarwell.keyserver.common.ids.KeyId;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Keys")
@Path("{repo}/key")
@ApplicationScoped
public class KeyEndpoint {

    @Inject
    KeyRepositoryService keyRepositoryService;

    @GET
    @Path("{keyid}")
    public Response getKeyByIdFromRepository(
            @PathParam("repo") String repoName, @PathParam("keyid") String keyIdString) {
        final RepositoryName repositoryName = RepositoryName.fromString(repoName);
        final KeyId keyId = KeyId.fromString(keyIdString);

        this.keyRepositoryService.getKeyByRepoAndKeyId(repositoryName, keyId);

        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }

    public KeyRepositoryService getRepositoryService() {
        return keyRepositoryService;
    }

    public void setRepositoryService(KeyRepositoryService keyRepositoryService) {
        this.keyRepositoryService = keyRepositoryService;
    }
}
