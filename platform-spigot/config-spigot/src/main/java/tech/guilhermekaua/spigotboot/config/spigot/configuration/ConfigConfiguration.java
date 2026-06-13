package tech.guilhermekaua.spigotboot.config.spigot.configuration;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.spigot.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;

import java.util.List;

@Configuration
public class ConfigConfiguration {
    @Bean
    public SpigotConfigManager configManager(
            Plugin plugin,
            @Nullable ConfigReferenceErrorHandler errorHandler,
            List<TypeSerializerRegistryCustomizer> serializerCustomizers
    ) {
        return new SpigotConfigManager(plugin, errorHandler, serializerCustomizers);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(SpigotConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }

    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(SpigotConfigManager configManager, Plugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }

    @Bean
    public TypeSerializerRegistryCustomizer bukkitTypeSerializers() {
        return new TypeSerializerRegistryCustomizer() {
            @Override
            public void customize(@NotNull TypeSerializerRegistry registry) {
                BukkitSerializers.registerAll(registry);
            }

            @Override
            public int getOrder() {
                return -100;
            }
        };
    }
}
