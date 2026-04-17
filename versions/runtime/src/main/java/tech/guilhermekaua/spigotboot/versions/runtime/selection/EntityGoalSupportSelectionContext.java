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
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;

import java.util.Objects;

/**
 * Immutable context used to resolve one runtime managed-goal support bundle.
 *
 * @since 2.0.2
 */
public final class EntityGoalSupportSelectionContext {
    private final MinecraftVersion minecraftVersion;
    private final VersionGoalSupportMetadata metadata;
    private final VersionGoalSupportProvider provider;

    /**
     * Creates a new managed-goal support selection context.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param metadata the resolved managed-goal support metadata
     * @param provider the optional managed-goal support provider
     */
    public EntityGoalSupportSelectionContext(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull VersionGoalSupportMetadata metadata,
            @Nullable VersionGoalSupportProvider provider
    ) {
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        this.provider = provider;
    }

    /**
     * Returns the resolved Minecraft version.
     *
     * @return the resolved Minecraft version
     */
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the resolved managed-goal support metadata.
     *
     * @return the resolved managed-goal support metadata
     */
    public @NotNull VersionGoalSupportMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the optional managed-goal support provider.
     *
     * @return the optional managed-goal support provider, or {@code null}
     */
    public @Nullable VersionGoalSupportProvider providerOrNull() {
        return provider;
    }
}
