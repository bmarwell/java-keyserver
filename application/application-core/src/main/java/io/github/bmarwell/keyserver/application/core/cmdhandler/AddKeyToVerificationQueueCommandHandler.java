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
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class AddKeyToVerificationQueueCommandHandler
        extends AbstractKeyServerCommandHandler<AddKeyToVerificationQueueCommand> {

    @Inject
    VerificationQueueRepository verificationQueueRepository;

    @Inject
    VerificationNotificationPort notificationPort;

    @Override
    public <C extends KeyServerCommand> boolean canHandle(C command) {
        return command instanceof AddKeyToVerificationQueueCommand;
    }

    @Override
    KeyServerCommandResponse doExecute(AddKeyToVerificationQueueCommand command) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void setVerificationQueueRepository(VerificationQueueRepository verificationQueueRepository) {
        this.verificationQueueRepository = verificationQueueRepository;
    }

    public void setNotificationPort(VerificationNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }
}
