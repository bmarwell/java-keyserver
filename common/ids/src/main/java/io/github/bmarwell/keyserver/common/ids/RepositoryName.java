/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids;

/**
 * A repository name must be unique and is therefore an identifier.
 */
public record RepositoryName(String value) {

    public static RepositoryName fromString(String repositoryName) {
        return new RepositoryName(repositoryName);
    }
}
