/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

public final class KeyserverTestImage {

    public static final String LIBERTY_BASE_IMAGE = "icr.io/appcafe/open-liberty:full-java25-openj9-ubi-minimal";
    public static final String LIBERTY_READY_LOG = ".*CWWKF0011I.*";

    private KeyserverTestImage() {}
}
