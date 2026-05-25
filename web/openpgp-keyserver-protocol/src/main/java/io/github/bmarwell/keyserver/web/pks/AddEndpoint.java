/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.CommandService;
import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.common.ids.IpAnonymizer;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "OpenPGP Keyserver Protocol: Add")
@Path("add")
public class AddEndpoint {

    @Inject
    CommandService commandService;

    @Context
    HttpServletRequest httpServletRequest;

    /// Accepts a PGP public key submitted as an HTML form post.
    ///
    /// The `keytext` parameter must contain an ASCII-armored public key block.
    /// The client IP is anonymized (last IPv4 octet / last 80 IPv6 bits zeroed)
    /// before it is stored anywhere.  `HttpServletRequest.getRemoteAddr()` provides
    /// the direct TCP peer address; when the server sits behind a reverse proxy the
    /// `Forwarded` / `X-Forwarded-For` headers should be validated and stripped by the
    /// proxy tier — this endpoint does **not** trust those headers directly to avoid
    /// IP-spoofing via forged headers.
    ///
    /// The command is dispatched asynchronously; this method returns `202 Accepted`
    /// immediately.  The caller should not assume the key is visible yet.
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addKey(@RequestBody @FormParam("keytext") String keyText) {
        String rawIp = httpServletRequest.getRemoteAddr();
        String anonymizedIp = IpAnonymizer.anonymize(rawIp);
        var command = new AddKeyToVerificationQueueCommand(keyText);
        var callerCtx = CommandCallerContext.of(anonymizedIp);
        commandService.handleCommand(command, callerCtx);
        return Response.accepted().build();
    }

    public void setCommandService(CommandService commandService) {
        this.commandService = commandService;
    }
}
