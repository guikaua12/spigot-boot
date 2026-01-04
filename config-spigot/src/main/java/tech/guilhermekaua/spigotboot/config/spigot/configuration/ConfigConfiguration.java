package tech.guilhermekaua.spigotboot.config.spigot.configuration;

import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigCollectionInjector;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;

@Configuration
public class ConfigConfiguration {
    @Bean
    public SpigotConfigManager configManager(Plugin plugin) {
        return new SpigotConfigManager(plugin);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configCollectionInjector(SpigotConfigManager configManager) {
        return (registry) -> {
            ConfigCollectionInjector collectionInjector = new ConfigCollectionInjector(configManager);
            registry.register(collectionInjector);
        };
    }
}
