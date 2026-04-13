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
package tech.guilhermekaua.spigotboot.entity.runtime.strategy;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;

/**
 * Version-local spawn and attach entrypoint used behind the shared runtime strategy seam.
 *
 * @since 2.0.2
 */
public interface EntityVersionEntrypoint {

    /**
     * Returns the immutable runtime capability summary for this exact-version entrypoint.
     *
     * @return the capability summary
     */
    @NotNull EntityVersionCapabilities capabilities();

    /**
     * Returns the immutable runtime binding bundle for this exact-version entrypoint.
     *
     * @return the binding bundle
     */
    @NotNull EntityVersionBindings bindings();

    /**
     * Returns whether this exact-version entrypoint supports the supplied logical base type.
     *
     * @param baseType the logical base type
     * @return {@code true} when the base type is supported
     */
    boolean supports(@NotNull CustomEntityBaseType baseType);

    /**
     * Executes the version-owned fresh-spawn implementation.
     *
     * @param template the template to spawn
     * @param spawnOptions the spawn options
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned controlled entity
     */
    <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );

    /**
     * Executes the version-owned attach or replacement implementation.
     *
     * @param entity the existing Bukkit entity
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live controlled entity
     */
    <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );
}
