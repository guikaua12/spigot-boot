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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the controller used for a single spawn.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
@FunctionalInterface
public interface SpawnControllerFactory<T extends Entity> {

    /**
     * Creates the controller for the supplied spawn context.
     *
     * @param context the spawn-time context
     * @return the controller instance
     */
    @NotNull EntityController<T> create(@NotNull SpawnContext<T> context);

    /**
     * Returns a pass-through controller factory.
     *
     * @param <T> the Bukkit entity type
     * @return a pass-through controller factory
     */
    static <T extends Entity> @NotNull SpawnControllerFactory<T> passThrough() {
        return new SpawnControllerFactory<T>() {
            @Override
            public @NotNull EntityController<T> create(@NotNull SpawnContext<T> context) {
                return new EntityController<T>() {
                };
            }
        };
    }
}
