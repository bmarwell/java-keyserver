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
package io.github.bmarwell.keyserver.application.api.commands;

import java.util.Optional;

/// Marker interface for all commands dispatched through the {@link
/// io.github.bmarwell.keyserver.application.api.CommandService}.
///
/// Commands may optionally carry a pre-anonymized caller IP by overriding
/// {@link #callerIp()}.  The command service reads this value and forwards it
/// to the business-transaction audit row without any explicit parameter threading
/// through the handler stack.
public interface KeyServerCommand {

    /// Returns the pre-anonymized caller IP for audit purposes.
    ///
    /// The default implementation returns {@link Optional#empty()}, which tells
    /// the command service to record {@code null} in the BTX row.
    /// Commands that capture a caller IP (e.g., HTTP-originated commands) should
    /// override this method and return the value that was already anonymized by
    /// {@code IpAnonymizer} before the command was constructed.
    ///
    /// @return the anonymized caller IP, or empty if unavailable
    default Optional<String> callerIp() {
        return Optional.empty();
    }
}
