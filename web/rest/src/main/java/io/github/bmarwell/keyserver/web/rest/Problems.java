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
    @Produces(MediaType.TEXT_HTML)
    public Response keyNotFound() {
        return Response.ok("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head><meta charset="utf-8"><title>Problem: Key Not Found</title></head>
                        <body>
                        <h1>Key Not Found</h1>
                        <p>No key matching the supplied identifier exists in this keyserver's published store.</p>
                        <dl>
                          <dt>HTTP status</dt><dd>404 Not Found</dd>
                        </dl>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML).build();
    }

    @GET
    @Path("duplicate-key")
    @Produces(MediaType.TEXT_HTML)
    public Response duplicateKey() {
        return Response.ok("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head><meta charset="utf-8"><title>Problem: Duplicate Key</title></head>
                        <body>
                        <h1>Duplicate Key</h1>
                        <p>The submitted key is already present in the published keystore.
                           This is treated as idempotent and the existing record is preserved.</p>
                        <dl>
                          <dt>HTTP status</dt><dd>200 OK</dd>
                        </dl>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML).build();
    }

    @GET
    @Path("key-parsing")
    @Produces(MediaType.TEXT_HTML)
    public Response keyParsing() {
        return Response.ok("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head><meta charset="utf-8"><title>Problem: Key Parsing Error</title></head>
                        <body>
                        <h1>Key Parsing Error</h1>
                        <p>The submitted key material could not be parsed as a valid OpenPGP key.</p>
                        <dl>
                          <dt>HTTP status</dt><dd>400 Bad Request</dd>
                        </dl>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML).build();
    }

    @GET
    @Path("key-validation")
    @Produces(MediaType.TEXT_HTML)
    public Response keyValidation() {
        return Response.ok("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head><meta charset="utf-8"><title>Problem: Key Validation Failed</title></head>
                        <body>
                        <h1>Key Validation Failed</h1>
                        <p>The submitted key is structurally valid but did not pass the keyserver's
                           validation rules (e.g. expired, revoked, no usable user IDs, unsupported algorithm).</p>
                        <dl>
                          <dt>HTTP status</dt><dd>400 Bad Request</dd>
                        </dl>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML).build();
    }

    @GET
    @Path("verification-error")
    @Produces(MediaType.TEXT_HTML)
    public Response verificationError() {
        return Response.ok("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head><meta charset="utf-8"><title>Problem: Verification Error</title></head>
                        <body>
                        <h1>Verification Error</h1>
                        <p>The email-verification request could not be completed.
                           The token may have expired or may not be valid for this keyserver.</p>
                        <dl>
                          <dt>HTTP status</dt><dd>400 Bad Request</dd>
                        </dl>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML).build();
    }
}
