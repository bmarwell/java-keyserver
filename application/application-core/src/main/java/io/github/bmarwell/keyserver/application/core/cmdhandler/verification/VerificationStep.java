/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

/// Small reusable verification/validation step in a larger verification chain.
public interface VerificationStep<I, O> {

    O verify(I input);
}
