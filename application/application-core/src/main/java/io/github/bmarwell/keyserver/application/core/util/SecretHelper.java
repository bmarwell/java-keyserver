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
package io.github.bmarwell.keyserver.application.core.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.Serial;
import java.io.Serializable;
import java.util.Base64;
import java.util.Locale;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.random.RandomGeneratorFactory;

@ApplicationScoped
public class SecretHelper implements Serializable {

    @Serial
    private static final long serialVersionUID = 1;

    private final SplittableGenerator RANDOM = (SplittableGenerator) RandomGeneratorFactory.all()
            .filter(RandomGeneratorFactory::isSplittable)
            .findFirst()
            .orElseThrow()
            .create(System.currentTimeMillis());

    public String createNewSecret() {
        byte[] secretBytes = new byte[24];
        RANDOM.split().nextBytes(secretBytes);
        return Base64.getEncoder()
                .encodeToString(secretBytes)
                .toLowerCase(Locale.ROOT)
                .replaceAll("=", "G")
                .replaceAll("/", "H")
                .replaceAll("\\+", "I");
    }
}
