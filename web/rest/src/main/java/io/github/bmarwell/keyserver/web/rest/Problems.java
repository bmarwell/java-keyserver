/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/// Human-readable documentation endpoint for RFC 7807 problem types.
///
/// Each `@GET` method corresponds to one logical problem type served by this keyserver.
/// The URI of each method is used as the {@code type} field in
/// {@link ProblemJson} responses produced by {@link KeyServerExceptionMapper}.
///
/// When adding a new {@code KeyServerException} subtype group:
/// <ol>
///   <li>Add a {@code @GET} method here with an appropriate {@code @Path} slug.</li>
///   <li>Add the corresponding slug mapping in {@link KeyServerExceptionMapper#problemSlug}.</li>
/// </ol>
@Path("/problems")
@ApplicationScoped
class Problems {

    @GET
    @Path("key-not-found")
    @Produces(MediaType.TEXT_PLAIN)
    public Response keyNotFound() {
        return Response.ok(
                        "Key Not Found\n\nNo key matching the supplied identifier exists in this keyserver's published store.\n\nHTTP status: 404 Not Found",
                        MediaType.TEXT_PLAIN)
                .build();
    }

    @GET
    @Path("duplicate-key")
    @Produces(MediaType.TEXT_PLAIN)
    public Response duplicateKey() {
        return Response.ok(
                        "Duplicate Key\n\nThe submitted key is already present in the published keystore. The existing record is preserved.\n\nHTTP status: 200 OK",
                        MediaType.TEXT_PLAIN)
                .build();
    }

    @GET
    @Path("key-parsing")
    @Produces(MediaType.TEXT_PLAIN)
    public Response keyParsing() {
        return Response.ok(
                        "Key Parsing Error\n\nThe submitted key material could not be parsed as a valid OpenPGP key.\n\nHTTP status: 400 Bad Request",
                        MediaType.TEXT_PLAIN)
                .build();
    }

    @GET
    @Path("key-validation")
    @Produces(MediaType.TEXT_PLAIN)
    public Response keyValidation() {
        return Response.ok(
                        "Key Validation Failed\n\nThe submitted key did not pass the keyserver's validation rules (e.g. expired, revoked, no usable user IDs, unsupported algorithm).\n\nHTTP status: 400 Bad Request",
                        MediaType.TEXT_PLAIN)
                .build();
    }

    @GET
    @Path("verification-error")
    @Produces(MediaType.TEXT_PLAIN)
    public Response verificationError() {
        return Response.ok(
                        "Verification Error\n\nThe email-verification request could not be completed. The token may have expired or may not be valid for this keyserver.\n\nHTTP status: 400 Bad Request",
                        MediaType.TEXT_PLAIN)
                .build();
    }
}
