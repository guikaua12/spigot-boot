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
package tech.guilhermekaua.spigotboot.entity.runtime.selection;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;

import java.util.Objects;

/**
 * Selects the packet transport backend family for one runtime profile.
 *
 * @since 2.0.2
 */
public final class EntityTransportFamilySelector {
    private static final MinecraftVersion MODERN_1_14_MINIMUM = MinecraftVersion.of(1, 14, 0);
    private static final MinecraftVersion MODERN_1_17_MINIMUM = MinecraftVersion.of(1, 17, 0);
    private static final MinecraftVersion MODERN_1_19_2_MINIMUM = MinecraftVersion.of(1, 19, 2);
    private static final MinecraftVersion LATEST_1_21_MINIMUM = MinecraftVersion.of(1, 21, 0);

    private EntityTransportFamilySelector() {
    }

    /**
     * Selects the transport family for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected transport family
     */
    public static @NotNull EntityTransportFamily select(@NotNull EntityNetworkRuntimeSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!hasTransportMetadata(context)) {
            return EntityTransportFamily.UNSPECIFIED;
        }
        if (context.minecraftVersion().compareTo(MODERN_1_14_MINIMUM) < 0) {
            return EntityTransportFamily.LEGACY_1_8_TO_1_13_2;
        }
        if (context.minecraftVersion().compareTo(MODERN_1_17_MINIMUM) < 0) {
            return EntityTransportFamily.MODERN_1_14_TO_1_16_5;
        }
        if (context.minecraftVersion().compareTo(MODERN_1_19_2_MINIMUM) < 0) {
            return EntityTransportFamily.MODERN_1_17_TO_1_18_2;
        }
        if (context.minecraftVersion().compareTo(LATEST_1_21_MINIMUM) < 0) {
            return EntityTransportFamily.MODERN_1_19_2_TO_1_20_6;
        }
        return EntityTransportFamily.LATEST_1_21_X;
    }

    private static boolean hasTransportMetadata(@NotNull EntityNetworkRuntimeSelectionContext context) {
        return context.bindings().freshSpawn().hasConstructorBindings()
                || context.bindings().freshSpawn().trackerEntryHandleAvailable()
                || context.bindings().freshSpawn().trackerStateHandleAvailable()
                || context.bindings().replacement().trackerEntryHandleAvailable()
                || context.bindings().replacement().trackerStateHandleAvailable();
    }
}
