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

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion;
import tech.guilhermekaua.spigotboot.placeholder.registry.fixture.FixturePlaceholderHandler;
import tech.guilhermekaua.spigotboot.placeholder.registry.fixture.FixturePlugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceholderRegistryComponentScanIntegrationTest {

    /**
     * Regression: a {@code @RegisterPlaceholder} handler is meta-annotated {@code @Component}, so the core
     * component scan already registers it as a bean during the SCAN phase. {@link PlaceholderRegistry#initialize()}
     * runs later (MODULES phase) and must <em>resolve</em> that existing bean rather than registering the handler a
     * second time. Re-registering throws "BeanDefinition with qualifier '...' already exists" and aborts plugin enable.
     */
    @Test
    void initializeDoesNotReRegisterScanRegisteredHandler() {
        DependencyManager dependencyManager = new DependencyManager();
        String basePackage = FixturePlaceholderHandler.class.getPackage().getName();

        // simulate the SCAN phase: the component scan registers @RegisterPlaceholder handlers because
        // @RegisterPlaceholder is itself meta-annotated @Component
        new ComponentRegistry().registerComponents(basePackage, dependencyManager);
        assertFalse(
                dependencyManager.getBeanDefinitionRegistry().getDefinitions(FixturePlaceholderHandler.class).isEmpty(),
                "precondition: the component scan must have registered the handler"
        );

        PlaceholderStore store = new PlaceholderStore();
        PAPIExpansion papiExpansion = mock(PAPIExpansion.class);
        when(papiExpansion.register()).thenReturn(true);
        // the mock's class lives in the fixture package, so the registry's discovery targets the handler above
        Plugin plugin = mock(FixturePlugin.class);

        PlaceholderRegistry registry = new PlaceholderRegistry(store, papiExpansion, plugin, dependencyManager);

        assertDoesNotThrow(registry::initialize);
        assertNotNull(
                store.findPlaceholderMetadata("fixture_value"),
                "the handler's @Placeholder method should be published into the store"
        );
    }
}
