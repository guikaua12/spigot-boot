package me.approximations.apxPlugin.module;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Component;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.module.annotations.ConditionalOnClass;
import org.bukkit.plugin.Plugin;

@Component
@RequiredArgsConstructor
public class ModuleManager {
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public void loadModules() {
        final ModuleDiscoveryService moduleDiscoveryService = new ModuleDiscoveryService(plugin);
        for (Class<? extends Module> moduleClass : moduleDiscoveryService.discoverModules()) {
            loadModule(moduleClass);
        }
    }

    private void loadModule(Class<? extends Module> moduleClass) {
        try {
            if (moduleClass.isAnnotationPresent(ConditionalOnClass.class)) {
                ConditionalOnClass conditionalOnClass = moduleClass.getAnnotation(ConditionalOnClass.class);

                for (Class<?> clazz : conditionalOnClass.value()) {
                    try {
                        Class.forName(clazz.getName());
                    } catch (Exception e) {
                        plugin.getLogger().info(
                                conditionalOnClass.message() != null ?
                                        conditionalOnClass.message() :
                                        "Skipping module '" + moduleClass.getName() + "' due to missing class: " + clazz.getName()
                        );
                        return;
                    }
                }
            }

            dependencyManager.registerDependency(moduleClass);
            Module module = dependencyManager.resolveDependency(moduleClass);

            module.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load module: '" + moduleClass.getName() + "'", e);
        }
    }
}
