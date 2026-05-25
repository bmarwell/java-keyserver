/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

import io.github.bmarwell.keyserver.common.ids.KeyId;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import java.util.Optional;

/**
 * Key handling core service.
 */
public interface KeyRepositoryService {

    void getKeyByRepoAndKeyId(RepositoryName repoName, KeyId keyId);

    Optional<PgpPublicKey> getKeyByKeyId(KeyId keyId);

    /// Looks up an ASCII-armored public-key block by HKP search string.
    ///
    /// The `search` parameter is the raw value of the HKP `search` query parameter.
    /// Supported formats: `0x<fingerprint>`, `0x<keyid>`, email address, and (when
    /// `exactMatch` is false) UID substring.
    ///
    /// Returns the armored key block for the first matching key, or empty if none found.
    ///
    /// @param search     HKP search string
    /// @param exactMatch if true, only exact fingerprint or email matches are returned
    Optional<String> getArmoredKeyBySearch(String search, boolean exactMatch);
}
