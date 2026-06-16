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
package tech.guilhermekaua.spigotboot.config.configuration;

import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.List;

@Configuration
public class ConfigConfiguration {
    @Bean
    public DefaultConfigManager configManager(
            BootPlugin plugin,
            @Nullable ConfigReferenceErrorHandler errorHandler,
            List<TypeSerializerRegistryCustomizer> serializerCustomizers
    ) {
        return new DefaultConfigManager(plugin, errorHandler, serializerCustomizers);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(DefaultConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }

    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(DefaultConfigManager configManager, BootPlugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }
}
