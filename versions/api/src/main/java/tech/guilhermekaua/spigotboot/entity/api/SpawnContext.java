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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only spawn-time context used while preparing a controller for a new entity.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface SpawnContext<T extends Entity> {

    /**
     * Returns the template driving this spawn.
     *
     * @return the immutable template
     */
    @NotNull EntityTemplate<T> template();

    /**
     * Returns the registered template id, or {@code null} for one-off spawns.
     *
     * @return the template id, or {@code null}
     */
    default @Nullable CustomEntityId templateId() {
        return template().id();
    }

    /**
     * Returns the logical vanilla base type.
     *
     * @return the logical base type
     */
    @NotNull CustomEntityBaseType baseType();

    /**
     * Returns the Bukkit type exposed to plugin code.
     *
     * @return the Bukkit entity type
     */
    @NotNull Class<T> bukkitType();

    /**
     * Returns the resolved Minecraft version that will host the entity.
     *
     * @return the resolved Minecraft version
     */
    @NotNull MinecraftVersion minecraftVersion();

    /**
     * Returns the immutable spawn options for this spawn.
     *
     * @return the spawn options
     */
    @NotNull SpawnOptions spawnOptions();

    /**
     * Returns the spawn location.
     *
     * @return the spawn location
     */
    default @NotNull Location location() {
        return spawnOptions().location();
    }

    /**
     * Returns the immutable spawn data.
     *
     * @return the spawn data
     */
    default @NotNull CustomEntityDataView data() {
        return spawnOptions().data();
    }
}
