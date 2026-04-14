/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.testPlugin.services;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads matrix autorun settings from system properties and environment variables.
 */
final class EntityMatrixRuntimeRequest {
    private static final String AUTO_RUN_PROPERTY = "entity.matrix.autoRun";
    private static final String LEGACY_AUTO_RUN_PROPERTY = "spigotboot.entityMatrix.autoRun";
    private static final String AUTO_RUN_ENVIRONMENT = "ENTITY_MATRIX_AUTO_RUN";
    private static final String LEGACY_AUTO_RUN_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_AUTO_RUN";

    private static final String SERVER_PROPERTY = "entity.matrix.server";
    private static final String LEGACY_SERVER_PROPERTY = "spigotboot.entityMatrix.server";
    private static final String SERVER_ENVIRONMENT = "ENTITY_MATRIX_SERVER";
    private static final String LEGACY_SERVER_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_SERVER";

    private static final String SCENARIO_PROPERTY = "entity.matrix.scenario";
    private static final String LEGACY_SCENARIO_PROPERTY = "spigotboot.entityMatrix.scenario";
    private static final String SCENARIO_ENVIRONMENT = "ENTITY_MATRIX_SCENARIO";
    private static final String LEGACY_SCENARIO_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_SCENARIO";

    private static final String OUTPUT_DIRECTORY_PROPERTY = "entity.matrix.outputDir";
    private static final String LEGACY_OUTPUT_DIRECTORY_PROPERTY = "spigotboot.entityMatrix.outputDir";
    private static final String OUTPUT_DIRECTORY_ENVIRONMENT = "ENTITY_MATRIX_OUTPUT_DIR";
    private static final String LEGACY_OUTPUT_DIRECTORY_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_OUTPUT_DIR";

    private static final String TRACE_FILE_PROPERTY = "entity.matrix.traceFile";
    private static final String LEGACY_TRACE_FILE_PROPERTY = "spigotboot.entityMatrix.traceFile";
    private static final String TRACE_FILE_ENVIRONMENT = "ENTITY_MATRIX_TRACE_FILE";
    private static final String LEGACY_TRACE_FILE_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_TRACE_FILE";

    private static final String ASSERTIONS_FILE_PROPERTY = "entity.matrix.assertionsFile";
    private static final String LEGACY_ASSERTIONS_FILE_PROPERTY = "spigotboot.entityMatrix.assertionsFile";
    private static final String ASSERTIONS_FILE_ENVIRONMENT = "ENTITY_MATRIX_ASSERTIONS_FILE";
    private static final String LEGACY_ASSERTIONS_FILE_ENVIRONMENT = "SPIGOTBOOT_ENTITY_MATRIX_ASSERTIONS_FILE";

    private EntityMatrixRuntimeRequest() {
    }

    static boolean isAutoRunRequested() {
        String value = firstNonBlank(
                System.getProperty(AUTO_RUN_PROPERTY),
                System.getenv(AUTO_RUN_ENVIRONMENT),
                System.getProperty(LEGACY_AUTO_RUN_PROPERTY),
                System.getenv(LEGACY_AUTO_RUN_ENVIRONMENT)
        );
        return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }

    static @Nullable String serverId() {
        return firstNonBlank(
                System.getProperty(SERVER_PROPERTY),
                System.getenv(SERVER_ENVIRONMENT),
                System.getProperty(LEGACY_SERVER_PROPERTY),
                System.getenv(LEGACY_SERVER_ENVIRONMENT)
        );
    }

    static @Nullable String scenarioId() {
        return firstNonBlank(
                System.getProperty(SCENARIO_PROPERTY),
                System.getenv(SCENARIO_ENVIRONMENT),
                System.getProperty(LEGACY_SCENARIO_PROPERTY),
                System.getenv(LEGACY_SCENARIO_ENVIRONMENT)
        );
    }

    static @Nullable Path outputDirectoryOverride() {
        return toPath(firstNonBlank(
                System.getProperty(OUTPUT_DIRECTORY_PROPERTY),
                System.getenv(OUTPUT_DIRECTORY_ENVIRONMENT),
                System.getProperty(LEGACY_OUTPUT_DIRECTORY_PROPERTY),
                System.getenv(LEGACY_OUTPUT_DIRECTORY_ENVIRONMENT)
        ));
    }

    static @Nullable Path traceFileOverride() {
        return toPath(firstNonBlank(
                System.getProperty(TRACE_FILE_PROPERTY),
                System.getenv(TRACE_FILE_ENVIRONMENT),
                System.getProperty(LEGACY_TRACE_FILE_PROPERTY),
                System.getenv(LEGACY_TRACE_FILE_ENVIRONMENT)
        ));
    }

    static @Nullable Path assertionsFileOverride() {
        return toPath(firstNonBlank(
                System.getProperty(ASSERTIONS_FILE_PROPERTY),
                System.getenv(ASSERTIONS_FILE_ENVIRONMENT),
                System.getProperty(LEGACY_ASSERTIONS_FILE_PROPERTY),
                System.getenv(LEGACY_ASSERTIONS_FILE_ENVIRONMENT)
        ));
    }

    private static @Nullable Path toPath(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return Paths.get(value);
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
