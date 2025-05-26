package me.approximations.apxPlugin.module;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.di.manager.DependencyManager;

@Component
@RequiredArgsConstructor
public class ModuleManager {
    private final ModuleDiscoveryService moduleDiscoveryService = new ModuleDiscoveryService();
    private final DependencyManager dependencyManager;

    public void loadModules() {
        for (Class<? extends Module> moduleClass : moduleDiscoveryService.discoverModules()) {
            loadModule(moduleClass);
        }
    }

    private void loadModule(Class<? extends Module> moduleClass) {
        try {
            dependencyManager.registerDependency(moduleClass);
            Module module = dependencyManager.resolveDependency(moduleClass);

            module.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load module: '" + moduleClass.getName() + "'", e);
        }
    }
}
