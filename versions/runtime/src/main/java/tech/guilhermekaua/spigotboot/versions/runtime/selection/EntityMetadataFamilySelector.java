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

import java.util.Objects;

/**
 * Selects the metadata synchronization backend family for one runtime profile.
 *
 * @since 2.0.2
 */
public final class EntityMetadataFamilySelector {
    private static final MinecraftVersion TRANSITIONAL_1_13_MINIMUM = MinecraftVersion.of(1, 13, 0);
    private static final MinecraftVersion MODERN_1_14_MINIMUM = MinecraftVersion.of(1, 14, 0);
    private static final MinecraftVersion MODERN_1_17_MINIMUM = MinecraftVersion.of(1, 17, 0);
    private static final MinecraftVersion LATEST_1_21_MINIMUM = MinecraftVersion.of(1, 21, 0);

    private EntityMetadataFamilySelector() {
    }

    /**
     * Selects the metadata family for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected metadata family
     */
    public static @NotNull EntityMetadataFamily select(@NotNull EntityNetworkRuntimeSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!hasMetadataSupport(context)) {
            return EntityMetadataFamily.UNSPECIFIED;
        }
        if (context.minecraftVersion().compareTo(TRANSITIONAL_1_13_MINIMUM) < 0) {
            return EntityMetadataFamily.LEGACY_DATA_WATCHER;
        }
        if (context.minecraftVersion().compareTo(MODERN_1_14_MINIMUM) < 0) {
            return EntityMetadataFamily.TRANSITIONAL_DATA_WATCHER_1_13;
        }
        if (context.minecraftVersion().compareTo(MODERN_1_17_MINIMUM) < 0) {
            return EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5;
        }
        if (context.minecraftVersion().compareTo(LATEST_1_21_MINIMUM) < 0) {
            return EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6;
        }
        return EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X;
    }

    private static boolean hasMetadataSupport(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.bindings().freshSpawn().trackerEntryHandleAvailable()
                || context.bindings().freshSpawn().trackerStateHandleAvailable()
                || context.bindings().replacement().trackerEntryHandleAvailable()
                || context.bindings().replacement().trackerStateHandleAvailable();
    }
}
