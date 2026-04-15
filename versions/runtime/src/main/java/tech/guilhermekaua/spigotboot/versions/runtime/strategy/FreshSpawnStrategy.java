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
package tech.guilhermekaua.spigotboot.versions.runtime.strategy;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;

/**
 * Executes the runtime-selected fresh-spawn path for a resolved version adapter.
 *
 * @since 2.0.2
 */
public interface FreshSpawnStrategy {

    /**
     * Returns the runtime-selected strategy id.
     *
     * @return the selected strategy id
     */
    @NotNull String id();

    /**
     * Spawns a fresh controlled entity using the resolved version adapter.
     *
     * @param adapter the resolved version adapter
     * @param template the template to spawn
     * @param spawnOptions the spawn options
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live spawned entity
     */
    <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull VersionAdapter adapter,
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );
}
