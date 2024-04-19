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
package io.github.bmarwell.keyserver.repository;

import io.github.bmarwell.keyserver.application.port.repository.KeyVerificationQueueRepository;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;

@Default
@Dependent
public class JpaKeyVerificationQueueRepository extends BaseRepository implements KeyVerificationQueueRepository {

    @Override
    public void addKeyToRepository(RepositoryName repositoryName, PgpPublicKey publicKey) {
        throw new UnsupportedOperationException("not implemented");
    }
}
