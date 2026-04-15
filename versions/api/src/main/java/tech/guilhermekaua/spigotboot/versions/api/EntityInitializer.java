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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes a spawned entity before its controller receives spawn callbacks.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
@FunctionalInterface
public interface EntityInitializer<T extends Entity> {

    /**
     * Initializes the supplied spawned entity.
     *
     * @param entity the live spawned entity
     */
    void initialize(@NotNull SpawnedEntity<T> entity);

    /**
     * Returns a no-op initializer.
     *
     * @param <T> the Bukkit entity type
     * @return a no-op initializer
     */
    static <T extends Entity> @NotNull EntityInitializer<T> noop() {
        return new EntityInitializer<T>() {
            @Override
            public void initialize(@NotNull SpawnedEntity<T> entity) {
            }
        };
    }
}
