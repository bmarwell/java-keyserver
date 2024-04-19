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
package io.github.bmarwell.keyserver.repository.tools;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

@ApplicationScoped
public class FlywayTool {

    private static final AtomicBoolean MIGRATION_RUN = new AtomicBoolean();

    private static final ReentrantLock MIGRATION_LOCK = new ReentrantLock();

    @Resource(name = "jdbc/keyserver")
    private DataSource ds;

    // like @Startup
    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        this.initDb();
    }

    public void initDb() {
        if (MIGRATION_RUN.get()) {
            return;
        }

        if (!MIGRATION_LOCK.tryLock()) {
            return;
        }

        try {
            if (MIGRATION_RUN.get()) {
                // already done
                return;
            }

            doMigrate();

            MIGRATION_RUN.set(true);
        } finally {
            MIGRATION_LOCK.unlock();
        }
    }

    private void doMigrate() {
        System.out.println("ds :: " + this.ds);
        try {
            final var flyway = Flyway.configure()
                    .loggers("slf4j")
                    .dataSource(ds)
                    .locations("classpath:/io/github/bmarwell/keyserver/repository/migrations")
                    .load();

            flyway.migrate();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot start app, flyway migration failed.", ex);
        }
    }
}
