/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.commands;

import org.jspecify.annotations.Nullable;

/// Caller metadata that travels alongside a command through the dispatch chain.
///
/// Unlike the command record (which carries business payload), `CommandCallerContext`
/// carries execution metadata — currently the pre-anonymized caller IP.
/// Both the command service and each command handler receive it explicitly so
/// the information is visible in method signatures rather than hidden behind CDI injection.
///
/// ## IP anonymization contract
///
/// The `anonymizedCallerIp` value **must** already be anonymized (IPv4 last octet
/// zeroed, IPv6 last 80 bits zeroed) by the primary adapter before this context
/// is constructed.  No component downstream should receive or store a raw IP.
///
/// Use {@link #empty()} when no caller IP is available (e.g., internally triggered commands).
public record CommandCallerContext(@Nullable String anonymizedCallerIp) {

    /// Returns a context with no caller IP (e.g., for internally triggered commands).
    public static CommandCallerContext empty() {
        return new CommandCallerContext(null);
    }

    /// Returns a context carrying a pre-anonymized caller IP.
    public static CommandCallerContext of(String anonymizedCallerIp) {
        return new CommandCallerContext(anonymizedCallerIp);
    }
}
