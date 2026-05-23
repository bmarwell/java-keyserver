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
package io.github.bmarwell.keyserver.repository.entity;

/// State of a business transaction.
///
/// `STARTED` is written synchronously before the command handler runs,
/// in a `REQUIRES_NEW` JTA transaction, so it is always durable.
/// `COMPLETED` and `FAILED` are written afterwards — also in `REQUIRES_NEW`
/// — so they survive a rollback of the command's own transaction.
public enum BusinessTransactionState {
    STARTED,
    COMPLETED,
    FAILED
}
