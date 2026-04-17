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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stable matrix scenario metadata used by the sample plugin and tests.
 */
final class EntityScenarioDescriptor {
    private static final Map<String, EntityScenarioDescriptor> DESCRIPTORS = createDescriptors();

    private final String id;
    private final Set<String> assertionKeys;

    private EntityScenarioDescriptor(@NotNull String id, @NotNull Set<String> assertionKeys) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.assertionKeys = Collections.unmodifiableSet(new LinkedHashSet<String>(
                Objects.requireNonNull(assertionKeys, "assertionKeys cannot be null")
        ));
    }

    static @NotNull Map<String, EntityScenarioDescriptor> all() {
        return DESCRIPTORS;
    }

    static @NotNull EntityScenarioDescriptor require(@NotNull String id) {
        EntityScenarioDescriptor descriptor = DESCRIPTORS.get(Objects.requireNonNull(id, "id cannot be null"));
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown entity demo scenario '" + id + "'.");
        }
        return descriptor;
    }

    @NotNull String id() {
        return id;
    }

    @NotNull Set<String> assertionKeys() {
        return assertionKeys;
    }

    private static @NotNull Map<String, EntityScenarioDescriptor> createDescriptors() {
        Map<String, EntityScenarioDescriptor> descriptors = new LinkedHashMap<String, EntityScenarioDescriptor>();
        register(descriptors, "orbit", "pass", "spawnCount", "duplicateRegistrationErrors", "movementSyncObserved");
        register(descriptors, "deathfx-cow", "pass", "aiReactedAfterHit", "deathEffectCount", "duplicateRegistrationErrors");
        register(
                descriptors,
                "deathfx-passive-family",
                "pass",
                "passCount",
                "failCount",
                "selectedBaseType",
                "aiReactedAfterHit",
                "deathEffectCount",
                "duplicateRegistrationErrors"
        );
        register(
                descriptors,
                "metadata-dirty-zombie",
                "pass",
                "metadataInitCount",
                "metadataDeltaCount",
                "attributeInitCount",
                "equipmentInitCount",
                "effectInitCount"
        );
        register(
                descriptors,
                "viewer-cycle-zombie",
                "pass",
                "viewerAddCount",
                "viewerRemoveCount",
                "spawnCount",
                "destroyCount"
        );
        register(
                descriptors,
                "viewer-cycle-special-family",
                "pass",
                "passCount",
                "failCount",
                "selectedBaseType",
                "viewerAddCount",
                "viewerRemoveCount",
                "spawnCount",
                "destroyCount"
        );
        register(
                descriptors,
                "attach-existing-zombie",
                "pass",
                "passCount",
                "failCount",
                "selectedBaseType",
                "attachCount",
                "controllerTickObserved",
                "duplicateSpawnCount",
                "entityIdStable",
                "trackerRebound"
        );
        return Collections.unmodifiableMap(descriptors);
    }

    private static void register(@NotNull Map<String, EntityScenarioDescriptor> descriptors, @NotNull String id, @NotNull String... keys) {
        descriptors.put(id, new EntityScenarioDescriptor(id, new LinkedHashSet<String>(Arrays.asList(keys))));
    }
}
