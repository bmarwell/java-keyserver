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
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.VerifyUidCommand;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/// Handles the email-verification link clicked by a key owner.
///
/// ## Async dispatch
///
/// The command is dispatched via the standard async `CommandService`.  This means
/// the response is `202 Accepted` and the caller does not receive inline success or
/// failure feedback.  A future iteration should add a synchronous invocation path
/// (or polling mechanism) so the user sees a meaningful confirmation page.
///
/// ## Caller context
///
/// The IP is not forwarded for the verification step: the token already serves as
/// a one-time credential and IP attribution here adds no audit value.
/// {@link CommandCallerContext#empty()} is used.
@Tag(name = "OpenPGP Keyserver Protocol: Verify")
@Path("verify")
public class VerifyEndpoint {

    @Inject
    CommandService commandService;

    /// Consumes a verification token from a link sent to the key owner's email
    /// address and schedules the corresponding UID for publication.
    ///
    /// @param token the TSID of the verification-queue entry, as an unsigned decimal string
    /// @return `202 Accepted` — the verification is processed asynchronously
    @GET
    @Path("{token}")
    @Operation(summary = "Confirm email ownership via verification link")
    public Response verify(@PathParam("token") String token) {
        var command = new VerifyUidCommand(token);
        commandService.handleCommand(command, CommandCallerContext.empty());
        return Response.accepted().build();
    }

    public void setCommandService(CommandService commandService) {
        this.commandService = commandService;
    }
}
