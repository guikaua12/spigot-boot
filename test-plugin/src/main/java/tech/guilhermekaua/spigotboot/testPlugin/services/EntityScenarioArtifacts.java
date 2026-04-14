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

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes deterministic machine-readable matrix artifacts for entity demo scenarios.
 */
final class EntityScenarioArtifacts {
    private EntityScenarioArtifacts() {
    }

    static @NotNull Path outputDirectory(@NotNull String scenarioId) {
        Path outputDirectoryOverride = EntityMatrixRuntimeRequest.outputDirectoryOverride();
        if (outputDirectoryOverride != null) {
            return outputDirectoryOverride;
        }

        String server = EntityMatrixRuntimeRequest.serverId();
        if (server == null || server.trim().isEmpty()) {
            server = Bukkit.getServer() == null ? "unknown-server" : sanitize(Bukkit.getName() + "-" + Bukkit.getVersion());
        }
        return Paths.get("target", "entity-matrix", sanitize(server), sanitize(scenarioId));
    }

    static void write(
            @NotNull String scenarioId,
            @NotNull List<Map<String, Object>> trace,
            @NotNull Map<String, Object> assertions
    ) {
        EntityScenarioDescriptor descriptor = EntityScenarioDescriptor.require(scenarioId);
        Path outputDirectory = outputDirectory(scenarioId);
        Map<String, Object> normalizedAssertions = new LinkedHashMap<String, Object>();
        for (String key : descriptor.assertionKeys()) {
            normalizedAssertions.put(key, assertions.get(key));
        }

        Map<String, Object> tracePayload = new LinkedHashMap<String, Object>();
        tracePayload.put("scenario", descriptor.id());
        tracePayload.put("server", outputDirectory.getParent() == null ? "unknown-server" : outputDirectory.getParent().getFileName().toString());
        tracePayload.put("events", new ArrayList<Map<String, Object>>(trace));

        try {
            Path traceFile = EntityMatrixRuntimeRequest.traceFileOverride();
            if (traceFile == null) {
                traceFile = outputDirectory.resolve("trace.json");
            }
            Path assertionsFile = EntityMatrixRuntimeRequest.assertionsFileOverride();
            if (assertionsFile == null) {
                assertionsFile = outputDirectory.resolve("assertions.json");
            }

            if (traceFile.getParent() != null) {
                Files.createDirectories(traceFile.getParent());
            }
            if (assertionsFile.getParent() != null) {
                Files.createDirectories(assertionsFile.getParent());
            }

            Files.write(traceFile, toJson(tracePayload).getBytes(StandardCharsets.UTF_8));
            Files.write(assertionsFile, toJson(normalizedAssertions).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write entity matrix artifacts for '" + scenarioId + "'.", exception);
        }
    }

    static void writeFailure(@NotNull String scenarioId, @NotNull Throwable failure) {
        EntityScenarioDescriptor descriptor = EntityScenarioDescriptor.require(scenarioId);
        Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        for (String key : descriptor.assertionKeys()) {
            assertions.put(key, "pass".equals(key) ? Boolean.FALSE : null);
        }

        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("message", failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
        details.put("exception", failure.getClass().getName());
        List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
        Map<String, Object> traceEntry = new LinkedHashMap<String, Object>();
        traceEntry.put("event", "autorun-failure");
        traceEntry.put("details", details);
        trace.add(traceEntry);
        write(scenarioId, trace, assertions);
    }

    private static @NotNull String toJson(@NotNull Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(quote(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof Collection) {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            boolean first = true;
            for (Object element : (Collection<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(toJson(element));
            }
            builder.append(']');
            return builder.toString();
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<Object>();
            Object[] array = (Object[]) value;
            for (Object element : array) {
                values.add(element);
            }
            return toJson(values);
        }
        return quote(String.valueOf(value));
    }

    private static @NotNull String quote(@NotNull String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(character);
                    break;
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static @NotNull String sanitize(@NotNull String value) {
        String trimmed = Objects.requireNonNull(value, "value cannot be null").trim();
        if (trimmed.isEmpty()) {
            return "unknown-server";
        }
        return trimmed.replaceAll("[^A-Za-z0-9._-]+", "-");
    }
}
