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
package tech.guilhermekaua.spigotboot.entity.api;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.capability.EntityCapabilities;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;

/**
 * Defines the contract that each version-specific entity implementation must provide.
 *
 * @since 2.0.2
 */
public interface EntityVersionAdapter {

    /**
     * Returns the lowest Minecraft version supported by this adapter.
     *
     * @return the lower bound
     */
    @NotNull MinecraftVersion minimumVersion();

    /**
     * Returns the highest Minecraft version supported by this adapter.
     *
     * @return the upper bound
     */
    @NotNull MinecraftVersion maximumVersion();

    /**
     * Returns the entity capability model exposed by this adapter.
     *
     * @return the capabilities for this adapter
     */
    @NotNull EntityCapabilities capabilities();

    /**
     * Checks whether this adapter can serve the supplied version.
     *
     * @param version the version to check
     * @return {@code true} when this adapter supports the supplied version
     * @throws NullPointerException when the version is null
     */
    default boolean supports(@NotNull MinecraftVersion version) {
        return version.isBetween(minimumVersion(), maximumVersion());
    }

    /**
     * Creates a custom entity for the supplied type and location request.
     *
     * @param entityType the entity type to spawn
     * @param spawnRequest the spawn request
     * @return the spawned entity abstraction
     * @throws NullPointerException when any argument is null
     * @throws RuntimeException when the entity cannot be created
     */
    @NotNull CustomEntity createEntity(@NotNull EntityTypeKey entityType, @NotNull EntitySpawnRequest spawnRequest);
}
