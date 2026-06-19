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

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.placeholder.converter.TypeConverterManager;
import tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class PlaceholderRegistryWiringTest {

    @BeforeEach
    void setUp() {
        // PAPIExpansion extends PlaceholderAPI's PlaceholderExpansion, so a Bukkit server must be present
        // when the container instantiates it.
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Regression: {@link PAPIExpansion} and {@link PlaceholderRegistry} must not depend on each other,
     * otherwise registering them as beans throws a circular dependency error at registration time. The
     * shared lookup lives in {@link PlaceholderStore}, which both depend on, so the definition graph stays
     * acyclic. This guards the registration (cycle-detection) path, not bean instantiation.
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

    /**
     * Regression: resolving the placeholder components through the container must yield a single shared
     * {@link PlaceholderStore} instance. The producer ({@link PlaceholderRegistry}) publishes placeholders
     * into the store and the consumer ({@link PAPIExpansion}) reads them back; if they were injected with
     * different store instances, published placeholders would be invisible to PlaceholderAPI requests.
     */
    @Test
    void resolvesPlaceholderComponentsSharingASinglePlaceholderStore() throws Exception {
        DependencyManager dependencyManager = new DependencyManager();

        // stub the leaf collaborators the placeholder components inject
        dependencyManager.registerDependency(Plugin.class, mock(Plugin.class), null, false);
        dependencyManager.registerDependency(TypeConverterManager.class, mock(TypeConverterManager.class), null, false);
        dependencyManager.registerDependency(DependencyManager.class, dependencyManager, null, false);

        // register the placeholder components exactly as the @Component scan would
        dependencyManager.registerDependency(PlaceholderStore.class, null, false, null, null);
        dependencyManager.registerDependency(PAPIExpansion.class, null, false, null, null);
        dependencyManager.registerDependency(PlaceholderRegistry.class, null, false, null, null);

        PlaceholderStore store = dependencyManager.resolveDependency(PlaceholderStore.class, null);
        PAPIExpansion expansion = dependencyManager.resolveDependency(PAPIExpansion.class, null);
        PlaceholderRegistry registry = dependencyManager.resolveDependency(PlaceholderRegistry.class, null);

        assertNotNull(store);
        assertNotNull(expansion);
        assertNotNull(registry);

        assertSame(store, readPlaceholderStore(PAPIExpansion.class, expansion));
        assertSame(store, readPlaceholderStore(PlaceholderRegistry.class, registry));
    }

    private static PlaceholderStore readPlaceholderStore(Class<?> declaringType, Object bean) throws Exception {
        Field field = declaringType.getDeclaredField("placeholderStore");
        field.setAccessible(true);
        return (PlaceholderStore) field.get(bean);
    }
}
