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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all registered {@link View} singletons and their frozen configurations.
 * The no-arg constructor is the test/bootstrap constructor used by tests and by the
 * open phase; the DI constructor (added in Task 17) wires discovery.
 */
@Component
@ApiStatus.Internal
public final class ViewRegistry {

    private final Map<Class<? extends View>, RegisteredView> registry = new LinkedHashMap<>();

    /**
     * Test/bootstrap constructor — no dependencies required.
     */
    public ViewRegistry() {
    }

    /**
     * Registers a view instance. Freezes the token table first, then calls
     * {@code onInit} on the view, then builds and validates the config.
     * A second registration of the same type is rejected.
     *
     * @param instance the view to register
     * @throws IllegalStateException when the type was already registered
     */
    public void register(@NotNull View instance) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Looks up the registration for a view type.
     *
     * @param type the view class
     * @return the registration, or empty when not registered
     */
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type) {
        return Optional.ofNullable(registry.get(type));
    }

    /**
     * Returns an unmodifiable view of all registered views.
     *
     * @return all registrations
     */
    public @NotNull Collection<RegisteredView> all() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
