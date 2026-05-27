/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import io.github.bmarwell.keyserver.application.api.VerificationService;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/// Handles the email-verification link clicked by a key owner.
///
/// ## Synchronous verification
///
/// The verification is performed synchronously via {@link VerificationService}.
/// The user receives an immediate HTML confirmation or error page rather than a
/// generic `202 Accepted` response.
///
/// ## Caller context
///
/// The IP is not forwarded for the verification step: the token already serves as
/// a one-time credential and IP attribution here adds no audit value.
@Tag(name = "OpenPGP Keyserver Protocol: Verify")
@Path("verify")
public class VerifyEndpoint {

    @Inject
    VerificationService verificationService;

    @Inject
    VerifyRenderer verifyRenderer;

    /// Consumes a verification token from a link sent to the key owner's email
    /// address, publishes the corresponding UID, and returns a human-readable HTML page.
    ///
    /// Returns {@code 200 OK} with a success page on successful verification, or
    /// {@code 400 Bad Request} with an explanatory error page if the token is
    /// invalid, already consumed, or expired.
    ///
    /// @param token the TSID of the verification-queue entry, as an unsigned decimal string
    /// @return HTML page describing the outcome
    @GET
    @Path("{token}")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Confirm email ownership via verification link")
    @APIResponse(responseCode = "200", description = "UID verified and published successfully")
    @APIResponse(responseCode = "400", description = "Token is invalid, already consumed, or expired")
    public Response verify(@PathParam("token") String token) {
        try {
            VerificationResult result = this.verificationService.verifyUid(token);
            return Response.ok(this.verifyRenderer.renderSuccess(result), "text/html;charset=utf-8")
                    .build();
        } catch (TokenExpiredException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(this.verifyRenderer.renderExpired())
                    .type("text/html;charset=utf-8")
                    .build();
        } catch (TokenInvalidException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(this.verifyRenderer.renderInvalid())
                    .type("text/html;charset=utf-8")
                    .build();
        }
    }

    // CDI-friendly setters for unit testing
    public void setVerificationService(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    public void setVerifyRenderer(VerifyRenderer verifyRenderer) {
        this.verifyRenderer = verifyRenderer;
    }
}
