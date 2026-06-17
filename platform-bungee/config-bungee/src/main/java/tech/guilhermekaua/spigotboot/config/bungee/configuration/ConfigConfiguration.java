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
package tech.guilhermekaua.spigotboot.config.bungee.configuration;

import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.config.bungee.serialization.BungeeConfigSerializers;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;

import java.util.List;

@Configuration
public class ConfigConfiguration {
    @Bean
    public BungeeConfigManager configManager(
            Plugin plugin,
            @Nullable ConfigReferenceErrorHandler errorHandler,
            List<TypeSerializerRegistryCustomizer> serializerCustomizers
    ) {
        return new BungeeConfigManager(plugin, errorHandler, serializerCustomizers);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(BungeeConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }

    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(BungeeConfigManager configManager, Plugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }

    @Bean
    public TypeSerializerRegistryCustomizer bungeeTypeSerializers() {
        return new TypeSerializerRegistryCustomizer() {
            @Override
            public void customize(@NotNull TypeSerializerRegistry registry) {
                BungeeConfigSerializers.registerAll(registry);
            }

            @Override
            public int getOrder() {
                return -100;
            }
        };
    }
}
