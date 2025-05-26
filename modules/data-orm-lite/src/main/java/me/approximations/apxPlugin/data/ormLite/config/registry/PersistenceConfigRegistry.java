package me.approximations.apxPlugin.data.ormLite.config.registry;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.data.ormLite.config.PersistenceConfig;
import me.approximations.apxPlugin.data.ormLite.config.registry.discovery.PersistenceConfigDiscoveryService;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.reflection.DiscoveryService;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class PersistenceConfigRegistry {
    private final DiscoveryService<Class<? extends PersistenceConfig>> persistenceConfigDiscoveryService = new PersistenceConfigDiscoveryService();
    private final AtomicReference<PersistenceConfig> persistenceConfigAtomicReference = new AtomicReference<>();
    private final DependencyManager dependencyManager;

    public PersistenceConfig initialize() {
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
