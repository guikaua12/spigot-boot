package tech.guilhermekaua.spigotboot.config.spigot.configuration;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigCollectionInjector;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;

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
    public CustomInjectorRegistryCustomizer configCollectionInjector(SpigotConfigManager configManager) {
        return (registry) -> {
            registry.register(new ConfigCollectionInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
        };
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
