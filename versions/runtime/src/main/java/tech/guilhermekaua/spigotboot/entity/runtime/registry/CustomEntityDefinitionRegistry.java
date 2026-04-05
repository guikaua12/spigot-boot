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
package tech.guilhermekaua.spigotboot.entity.runtime.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores registered logical custom entity definitions for one runtime instance.
 *
 * @since 2.0.2
 */
public final class CustomEntityDefinitionRegistry {
    private final Map<CustomEntityId, CustomEntityDefinition<?>> definitions =
            new LinkedHashMap<CustomEntityId, CustomEntityDefinition<?>>();

    /**
     * Registers the supplied definition.
     *
     * @param definition the definition to register
     */
    public synchronized void register(@NotNull CustomEntityDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        CustomEntityDefinition<?> previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null && previous != definition) {
            throw new IllegalArgumentException(
                    "A custom entity definition is already registered for '" + definition.id() + "'."
            );
        }
    }

    /**
     * Returns the definition associated with the supplied id, or {@code null} when it does not exist.
     *
     * @param id the logical definition id
     * @return the registered definition, or {@code null}
     */
    public synchronized @Nullable CustomEntityDefinition<?> find(@NotNull CustomEntityId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return definitions.get(id);
    }

    /**
     * Returns every registered definition.
     *
     * @return the registered definitions
     */
    public synchronized @NotNull Collection<CustomEntityDefinition<?>> definitions() {
        return Collections.unmodifiableList(new ArrayList<CustomEntityDefinition<?>>(definitions.values()));
    }
}
