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
import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.common.ids.IpAnonymizer;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "OpenPGP Keyserver Protocol: Add")
@Path("add")
public class AddEndpoint {

    @Inject
    CommandService commandService;

    @Context
    UriInfo uriInfo;

    /// Accepts a PGP public key submitted as an HTML form post.
    ///
    /// The `keytext` parameter must contain an ASCII-armored public key block.
    /// The client IP is anonymized (last IPv4 octet / last 80 IPv6 bits zeroed)
    /// before it is stored anywhere.
    ///
    /// The command is dispatched asynchronously; this method returns `202 Accepted`
    /// immediately.  The caller should not assume the key is visible yet.
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addKey(@RequestBody @FormParam("keytext") String keyText, @Context Request request) {
        String rawIp = uriInfo.getRequestUri().getHost();
        String anonymizedIp = IpAnonymizer.anonymize(rawIp);
        var command = new AddKeyToVerificationQueueCommand(keyText, anonymizedIp);
        commandService.handleCommand(command);
        return Response.accepted().build();
    }

    public void setCommandService(CommandService commandService) {
        this.commandService = commandService;
    }
}
