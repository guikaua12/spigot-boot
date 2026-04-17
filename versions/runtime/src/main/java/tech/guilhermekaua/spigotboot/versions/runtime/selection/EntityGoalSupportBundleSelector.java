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
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;

import java.util.Objects;

/**
 * Central runtime selector that composes the optional managed-goal support seam.
 *
 * @since 2.0.2
 */
public final class EntityGoalSupportBundleSelector {

    private EntityGoalSupportBundleSelector() {
    }

    /**
     * Selects a composed managed-goal support bundle for the supplied runtime metadata.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param metadata the resolved managed-goal support metadata
     * @param provider the optional managed-goal support provider
     * @return the selected managed-goal support bundle
     */
    public static @NotNull EntityGoalSupportBundle select(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull VersionGoalSupportMetadata metadata,
            VersionGoalSupportProvider provider
    ) {
        return select(new EntityGoalSupportSelectionContext(minecraftVersion, metadata, provider));
    }

    /**
     * Selects a composed managed-goal support bundle for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected managed-goal support bundle
     */
    public static @NotNull EntityGoalSupportBundle select(@NotNull EntityGoalSupportSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        return new EntityGoalSupportBundle(
                selectFamily(context),
                context.metadata(),
                context.providerOrNull()
        );
    }

    private static @NotNull EntityGoalSupportFamily selectFamily(@NotNull EntityGoalSupportSelectionContext context) {
        if (context.providerOrNull() == null) {
            return EntityGoalSupportFamily.UNSPECIFIED;
        }

        MinecraftVersion minecraftVersion = context.minecraftVersion();
        if (minecraftVersion.compareTo(MinecraftVersion.of(1, 13, 0)) < 0) {
            return EntityGoalSupportFamily.LEGACY_1_8_TO_1_12;
        }
        if (minecraftVersion.compareTo(MinecraftVersion.of(1, 14, 0)) < 0) {
            return EntityGoalSupportFamily.TRANSITIONAL_1_13;
        }
        if (minecraftVersion.compareTo(MinecraftVersion.of(1, 21, 0)) < 0) {
            return EntityGoalSupportFamily.MODERN_1_14_TO_1_20_6;
        }
        return EntityGoalSupportFamily.LATEST_1_21_X;
    }
}
