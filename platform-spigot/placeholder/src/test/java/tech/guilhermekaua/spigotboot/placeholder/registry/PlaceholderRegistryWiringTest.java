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
package tech.guilhermekaua.spigotboot.placeholder.registry;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlaceholderRegistryWiringTest {

    /**
     * Regression: {@link PAPIExpansion} and {@link PlaceholderRegistry} must not depend on each other,
     * otherwise registering both as beans throws a circular dependency error. The shared lookup lives in
     * {@link PlaceholderStore}, which both depend on, so the graph stays acyclic.
     */
    @Test
    void placeholderComponentsRegisterWithoutCircularDependency() {
        DependencyManager dependencyManager = new DependencyManager();

        assertDoesNotThrow(() -> {
            dependencyManager.registerDependency(PlaceholderStore.class, null, false, null, null);
            dependencyManager.registerDependency(PAPIExpansion.class, null, false, null, null);
            dependencyManager.registerDependency(PlaceholderRegistry.class, null, false, null, null);
        });
    }
}
