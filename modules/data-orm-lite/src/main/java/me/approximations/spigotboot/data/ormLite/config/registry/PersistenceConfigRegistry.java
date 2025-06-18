package me.approximations.spigotboot.data.ormLite.config.registry;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.data.ormLite.config.PersistenceConfig;
import me.approximations.spigotboot.data.ormLite.config.registry.discovery.PersistenceConfigDiscoveryService;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class PersistenceConfigRegistry {
    private final AtomicReference<PersistenceConfig> persistenceConfigAtomicReference = new AtomicReference<>();
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public PersistenceConfig initialize() {
        final DiscoveryService<Class<? extends PersistenceConfig>> persistenceConfigDiscoveryService = new PersistenceConfigDiscoveryService(plugin);
        Class<? extends PersistenceConfig> persistenceConfigClass = persistenceConfigDiscoveryService.discover().orElseThrow(
                () -> new IllegalStateException("data-orm-lite is on classpath but no persistence configuration is found. Ensure that a valid PersistenceConfig is provided.")
        );

        dependencyManager.registerDependency(persistenceConfigClass);
        PersistenceConfig persistenceConfig = dependencyManager.resolveDependency(persistenceConfigClass);

        persistenceConfigAtomicReference.set(persistenceConfig);

        return persistenceConfig;
    }

    public PersistenceConfig getPersistenceConfig() {
        return persistenceConfigAtomicReference.get();
    }
}
