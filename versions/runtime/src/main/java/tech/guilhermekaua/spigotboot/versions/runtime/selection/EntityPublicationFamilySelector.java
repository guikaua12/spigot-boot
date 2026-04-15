/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.selection;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;

import java.util.Objects;

/**
 * Selects the publication/add-remove backend family for one runtime profile.
 *
 * @since 2.0.2
 */
public final class EntityPublicationFamilySelector {
    private static final MinecraftVersion ENTITIES_BY_UUID_MINIMUM = MinecraftVersion.of(1, 14, 0);
    private static final MinecraftVersion SECTION_MANAGER_MINIMUM = MinecraftVersion.of(1, 17, 0);
    private static final MinecraftVersion PAPER_CHUNK_SYSTEM_MINIMUM = MinecraftVersion.of(1, 19, 2);
    private static final MinecraftVersion PAPER_MOONRISE_MINIMUM = MinecraftVersion.of(1, 21, 0);

    private EntityPublicationFamilySelector() {
    }

    /**
     * Selects the publication family for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected publication family
     */
    public static @NotNull EntityPublicationFamily select(@NotNull EntityNetworkRuntimeSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!hasPublicationMetadata(context)) {
            return EntityPublicationFamily.UNSPECIFIED;
        }
        if (context.minecraftVersion().compareTo(ENTITIES_BY_UUID_MINIMUM) < 0) {
            return EntityPublicationFamily.LEGACY_WORLD_LISTENER;
        }
        if (context.minecraftVersion().compareTo(SECTION_MANAGER_MINIMUM) < 0) {
            return EntityPublicationFamily.ENTITIES_BY_UUID;
        }
        if (isPaperMoonriseChunkSystem(context)) {
            return EntityPublicationFamily.PAPER_MOONRISE_CHUNK_SYSTEM;
        }
        if (isPaperChunkSystem(context)) {
            return EntityPublicationFamily.PAPER_CHUNK_SYSTEM;
        }
        return EntityPublicationFamily.SECTION_MANAGER;
    }

    private static boolean hasPublicationMetadata(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.capabilities().freshSpawnWorldRegistrationMode() != EntityWorldRegistrationMode.UNSPECIFIED
                || context.capabilities().replacementWorldRegistrationMode() != EntityWorldRegistrationMode.UNSPECIFIED
                || context.bindings().freshSpawn().worldRegistrationMode() != EntityWorldRegistrationMode.UNSPECIFIED
                || context.bindings().replacement().worldRegistrationMode() != EntityWorldRegistrationMode.UNSPECIFIED;
    }

    private static boolean isPaperChunkSystem(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.runtimeProfile().serverFlavor() == RuntimeServerFlavor.PAPER
                && context.minecraftVersion().isAtLeast(PAPER_CHUNK_SYSTEM_MINIMUM)
                && context.runtimeProfile().paperChunkSystemAvailable()
                && !context.runtimeProfile().paperMoonriseChunkSystemAvailable();
    }

    private static boolean isPaperMoonriseChunkSystem(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.runtimeProfile().serverFlavor() == RuntimeServerFlavor.PAPER
                && context.minecraftVersion().isAtLeast(PAPER_MOONRISE_MINIMUM)
                && context.runtimeProfile().paperMoonriseChunkSystemAvailable();
    }
}
