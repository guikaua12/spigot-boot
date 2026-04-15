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
 * Selects the tracker-hook backend family for one runtime profile.
 *
 * @since 2.0.2
 */
public final class EntityTrackerHookFamilySelector {
    private static final MinecraftVersion MODERN_TRACKER_MINIMUM = MinecraftVersion.of(1, 14, 0);

    private EntityTrackerHookFamilySelector() {
    }

    /**
     * Selects the tracker-hook family for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected tracker-hook family
     */
    public static @NotNull EntityTrackerHookFamily select(@NotNull EntityNetworkRuntimeSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!hasTrackingMetadata(context)) {
            return EntityTrackerHookFamily.UNSPECIFIED;
        }
        if (context.minecraftVersion().compareTo(MODERN_TRACKER_MINIMUM) < 0) {
            return EntityTrackerHookFamily.LEGACY_ENTRY_HOOK;
        }
        return EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE;
    }

    private static boolean hasTrackingMetadata(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.bindings().freshSpawn().trackerEntryHandleAvailable()
                || context.bindings().freshSpawn().trackerStateHandleAvailable()
                || context.bindings().replacement().trackerEntryHandleAvailable()
                || context.bindings().replacement().trackerStateHandleAvailable()
                || context.capabilities().trackerStateHandleAvailable();
    }
}
