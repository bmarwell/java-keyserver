/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.KeyIndexResult;
import io.github.bmarwell.keyserver.application.api.KeyRepositoryService;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.common.ids.KeyId;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Default
@ApplicationScoped
public class PersistentKeyRepositoryService implements KeyRepositoryService, Serializable {

    @Inject
    KeyRepository keyRepository;

    @Override
    public void getKeyByRepoAndKeyId(RepositoryName repoName, KeyId keyId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<PgpPublicKey> getKeyByKeyId(KeyId keyId) {
        return keyRepository.findBySearch(keyId.valueWithHexPrefix(), true).map(result -> result::fingerprint);
    }

    @Override
    public Optional<String> getArmoredKeyBySearch(String search, boolean exactMatch) {
        return keyRepository.findBySearch(search, exactMatch).map(KeyRepository.KeySearchResult::armoredKey);
    }

    @Override
    public List<KeyIndexResult> searchForIndex(String search, boolean exactMatch) {
        return this.keyRepository.findManyBySearch(search, exactMatch);
    }

    // CDI-friendly setter for unit testing
    public void setKeyRepository(KeyRepository keyRepository) {
        this.keyRepository = keyRepository;
    }
}
