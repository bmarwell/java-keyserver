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
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.KeyQueueRepositoryService;
import io.github.bmarwell.keyserver.application.port.repository.KeyVerificationQueueDao;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class PersistentKeyQueueRepositoryService implements KeyQueueRepositoryService {

    @Inject
    KeyVerificationQueueDao keyVerificationQueueDao;

    @Override
    public PgpPublicKey addKeyToRepository(RepositoryName repositoryName, PgpPublicKey publicKey) {
        return this.keyVerificationQueueDao.addKeyToQueue(repositoryName, publicKey);
    }

    public KeyVerificationQueueDao getKeyVerificationQueueDao() {
        return keyVerificationQueueDao;
    }

    public void setKeyVerificationQueueDao(KeyVerificationQueueDao keyVerificationQueueDao) {
        this.keyVerificationQueueDao = keyVerificationQueueDao;
    }
}
