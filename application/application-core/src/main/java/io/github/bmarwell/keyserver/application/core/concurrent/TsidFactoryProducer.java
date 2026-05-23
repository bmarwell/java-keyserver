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
package io.github.bmarwell.keyserver.application.core.concurrent;

import io.hypersistence.tsid.TSID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/// CDI producer for the shared, node-aware `TSID.Factory`.
///
/// ## Node ID
///
/// The TSID bit layout with a 10-bit node field:
///
/// | Bits | Content                  |
/// |------|--------------------------|
/// |  42  | Millisecond timestamp    |
/// |  10  | Node ID (0–1023)         |
/// |  12  | Per-ms sequence counter  |
///
/// The node ID is read from the `TSID_NODE` environment variable at startup.
/// Each server instance in a cluster must be assigned a distinct value in the
/// range `0–1023`; this supports up to **1 024 concurrent nodes** with
/// **4 096 unique IDs per node per millisecond**.
///
/// If `TSID_NODE` is absent or unparseable the factory defaults to node `0`,
/// which is safe for single-instance deployments.
@ApplicationScoped
public class TsidFactoryProducer {

    private static final int NODE_BITS = 10;

    @Produces
    @ApplicationScoped
    public TSID.Factory produceTsidFactory() {
        int node = resolveNode();
        return TSID.Factory.builder().withNodeBits(NODE_BITS).withNode(node).build();
    }

    private static int resolveNode() {
        String raw = System.getenv("TSID_NODE");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int node = Integer.parseInt(raw.strip());
            if (node < 0 || node >= (1 << NODE_BITS)) {
                return 0;
            }
            return node;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
