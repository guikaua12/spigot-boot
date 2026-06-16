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
package tech.guilhermekaua.spigotboot.config.bungee.test.configuration;

import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.config.bungee.configuration.ConfigConfiguration;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.DefaultCustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigConfigurationTest {

    @Mock
    Plugin plugin;

    @Test
    void onConfigReloadProcessor_registersProcessor() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        BungeeConfigManager configManager = mock(BungeeConfigManager.class);

        ConfigConfiguration configuration = new ConfigConfiguration();
        BeanPostProcessorRegistryCustomizer customizer = configuration.onConfigReloadProcessor(configManager, plugin);

        List<BeanPostProcessor> registered = new ArrayList<>();
        customizer.customize(registered::add);

        assertEquals(1, registered.size());
        assertTrue(registered.get(0) instanceof OnConfigReloadProcessor);
    }

    @Test
    void configInjectors_registersConfigValueInjector() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger(ConfigConfigurationTest.class.getName()));
        BungeeConfigManager configManager = new BungeeConfigManager(plugin);

        CustomInjectorRegistryCustomizer customizer = new ConfigConfiguration().configInjectors(configManager);
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        customizer.customize(registry);

        assertEquals(3, registry.getInjectors().size());
        assertTrue(registry.getInjectors().stream().anyMatch(i -> i instanceof FolderConfigInjector));
        assertTrue(registry.getInjectors().stream().anyMatch(i -> i instanceof ConfigRefInjector));
        assertTrue(registry.getInjectors().stream().anyMatch(i -> i instanceof ConfigValueInjector));
    }
}
