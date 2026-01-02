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
package tech.guilhermekaua.spigotboot.core.context.dependency.injector;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Registry that holds and manages custom injectors.
 * <p>
 * This registry maintains an ordered list of custom injectors. Injectors are sorted
 * by their {@link CustomInjector#getOrder()} value, with lower values having higher priority.
 * When multiple injectors have the same order, they are processed in registration order.
 * <p>
 * The registry is thread-safe and can be modified during the application lifecycle.
 */
public interface CustomInjectorRegistry {

    /**
     * Registers a custom injector.
     * <p>
     * The injector will be added to the registry and the sorted view will be updated.
     * Injectors are ordered by their {@link CustomInjector#getOrder()} value.
     *
     * @param injector the custom injector to register, not null
     * @throws NullPointerException if injector is null
     */
    void register(@NotNull CustomInjector injector);

    /**
     * Unregisters a custom injector.
     *
     * @param injector the custom injector to remove, not null
     * @return true if the injector was found and removed, false otherwise
     */
    boolean unregister(@NotNull CustomInjector injector);

    /**
     * Returns an unmodifiable ordered view of all registered injectors.
     * <p>
     * The list is sorted by {@link CustomInjector#getOrder()}, with lower values first.
     *
     * @return an unmodifiable list of injectors in order of priority
     */
    @NotNull List<CustomInjector> getInjectors();

    /**
     * Checks if any registered custom injector supports the given injection point.
     *
     * @param injectionPoint the injection point to check
     * @return true if any custom injector supports this injection point
     */
    boolean customInjectorSupported(@NotNull InjectionPoint injectionPoint);

    /**
     * Clears all registered injectors.
     */
    void clear();

    /**
     * Returns the number of registered injectors.
     *
     * @return the count of registered injectors
     */
    int size();

    /**
     * Checks if the registry is empty.
     *
     * @return true if no injectors are registered
     */
    boolean isEmpty();
}
