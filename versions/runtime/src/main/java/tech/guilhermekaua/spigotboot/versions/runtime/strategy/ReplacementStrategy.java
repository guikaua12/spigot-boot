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
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;

/**
 * Executes the runtime-selected replacement or attach path for an existing entity.
 *
 * @since 2.0.2
 */
public interface ReplacementStrategy {

    /**
     * Returns the runtime-selected strategy id.
     *
     * @return the selected strategy id
     */
    @NotNull String id();

    /**
     * Attaches or replaces the supplied entity using the resolved version adapter.
     *
     * @param adapter the resolved version adapter
     * @param entity the existing Bukkit entity
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live controlled entity
     */
    <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull VersionAdapter adapter,
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );
}
