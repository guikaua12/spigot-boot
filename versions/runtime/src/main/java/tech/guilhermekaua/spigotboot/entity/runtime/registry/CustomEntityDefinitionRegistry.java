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
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;

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
    private final Map<CustomEntityId, EntityTemplate<?>> definitions =
            new LinkedHashMap<CustomEntityId, EntityTemplate<?>>();

    /**
     * Registers the supplied definition.
     *
     * @param template the template to register
     */
    public synchronized void register(@NotNull EntityTemplate<?> template) {
        Objects.requireNonNull(template, "template cannot be null");
        CustomEntityId templateId = Objects.requireNonNull(template.id(), "template id cannot be null");
        EntityTemplate<?> previous = definitions.putIfAbsent(templateId, template);
        if (previous != null && previous != template) {
            throw new IllegalArgumentException(
                    "A custom entity definition is already registered for '" + templateId + "'."
            );
        }
    }

    /**
     * Returns the definition associated with the supplied id, or {@code null} when it does not exist.
     *
     * @param id the logical definition id
     * @return the registered definition, or {@code null}
     */
    public synchronized @Nullable EntityTemplate<?> find(@NotNull CustomEntityId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return definitions.get(id);
    }

    /**
     * Returns every registered definition.
     *
     * @return the registered definitions
     */
    public synchronized @NotNull Collection<EntityTemplate<?>> definitions() {
        return Collections.unmodifiableList(new ArrayList<EntityTemplate<?>>(definitions.values()));
    }
}
