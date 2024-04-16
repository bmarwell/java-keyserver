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

import io.github.bmarwell.keyserver.application.api.CommandService;
import io.github.bmarwell.keyserver.application.api.KeyRepositoryService;
import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "OpenPGP Keyserver Protocol: Add")
@Path("add")
public class AddEndpoint {

    @Inject
    KeyRepositoryService keyRepositoryService;

    @Inject
    CommandService commandService;

    @POST
    public Response addKey(@RequestBody InputStream requestBody, @QueryParam("options") String options) {
        AddKeyToVerificationQueueCommand command =
                new AddKeyToVerificationQueueCommand(requestBody, RepositoryName.fromString("LOCAL"));

        this.commandService.handleCommand(command);

        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    public KeyRepositoryService getRepositoryService() {
        return keyRepositoryService;
    }

    public void setRepositoryService(KeyRepositoryService keyRepositoryService) {
        this.keyRepositoryService = keyRepositoryService;
    }
}
