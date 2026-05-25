/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

public abstract class BaseRepository {

    @Inject
    private EntityManager entityManager;

    public BaseRepository() {}

    public EntityManager getEntityManager() {
        return entityManager;
    }
}
