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

/**
 * Interface for custom dependency injectors that can participate in the resolution process.
 * <p>
 * Custom injectors allow modules to provide alternative ways of resolving dependencies
 * beyond the standard bean lookup. For example, a configuration module might provide
 * an injector that resolves annotated parameters from configuration values.
 * <p>
 * Injectors are consulted in order (based on {@link #getOrder()}) before falling back
 * to the default resolution. An injector should return {@link InjectionResult#notHandled()}
 * if it doesn't want to handle a particular injection point, allowing the next injector
 * or default resolution to proceed.
 */
public interface CustomInjector {

    /**
     * Determines whether this injector can handle the given injection point.
     * <p>
     * This method should be fast as it's called for every injection point.
     * Complex logic should be deferred to {@link #resolve(InjectionPoint)}.
     *
     * @param injectionPoint the injection point to check, not null
     * @return true if this injector should attempt to resolve this injection point
     */
    boolean supports(@NotNull InjectionPoint injectionPoint);

    /**
     * Attempts to resolve a dependency for the given injection point.
     * <p>
     * This method is only called if {@link #supports(InjectionPoint)} returned true.
     * The injector should return:
     * <ul>
     *   <li>{@link InjectionResult#handled(Object)} if it successfully resolved the dependency</li>
     *   <li>{@link InjectionResult#notHandled()} if it cannot resolve this particular injection
     *       and wants the framework to try other injectors or default resolution</li>
     * </ul>
     *
     * @param injectionPoint the injection point to resolve, not null
     * @return the resolution result, never null
     */
    @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint);

    /**
     * Returns the order of this injector.
     * <p>
     * Injectors with lower order values are consulted first. The default order is 0.
     * Use negative values for high-priority injectors, positive values for low-priority ones.
     *
     * @return the order value for this injector
     */
    default int getOrder() {
        return 0;
    }
}
