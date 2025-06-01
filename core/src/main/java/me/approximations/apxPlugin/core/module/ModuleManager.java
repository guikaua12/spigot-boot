package me.approximations.apxPlugin.core.module;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.ComponentManager;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import me.approximations.apxPlugin.core.context.configuration.processor.ConfigurationProcessor;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.module.annotations.ConditionalOnClass;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Component
@RequiredArgsConstructor
public class ModuleManager {
    private final DependencyManager dependencyManager;
    private final ComponentManager componentManager;
    private final ConfigurationProcessor configurationProcessor;
    private final MethodHandlerProcessor methodHandlerProcessor;
    private final Plugin plugin;

    public void loadModules() {
        final ModuleDiscoveryService moduleDiscoveryService = new ModuleDiscoveryService();
        for (Class<? extends Module> moduleClass : moduleDiscoveryService.discoverModules()) {
            loadModule(moduleClass);
        }
    }

    private void loadModule(Class<? extends Module> moduleClass) {
        try {
            if (!verifyModuleDependencies(moduleClass)) {
                return;
            }

            componentManager.registerComponents(moduleClass);
            configurationProcessor.processFromPackage(moduleClass);
            MethodHandlerRegistry.registerAll(
                    methodHandlerProcessor.processFromPackage(
                            moduleClass
                    )
            );
            dependencyManager.registerDependency(moduleClass);

            Module module = dependencyManager.resolveDependency(moduleClass);

            module.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load module: '" + moduleClass.getName() + "'", e);
        }
    }

    private boolean verifyModuleDependencies(@NotNull Class<? extends Module> moduleClass) {
        if (!moduleClass.isAnnotationPresent(ConditionalOnClass.class)) {
            return true;
        }

        ConditionalOnClass conditionalOnClass = moduleClass.getAnnotation(ConditionalOnClass.class);

        String className = null;

        try {
            for (Class<?> clazz : conditionalOnClass.value()) {
                className = clazz.getName();
                Class.forName(clazz.getName());
            }
            return true;
        } catch (TypeNotPresentException e) {
            plugin.getLogger().info(
                    conditionalOnClass.message() != null ?
                            conditionalOnClass.message() :
                            "Skipping module '" + moduleClass.getName() + "' due to missing class: " + e.typeName()
            );
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info(
                    conditionalOnClass.message() != null ?
                            conditionalOnClass.message() :
                            "Skipping module '" + moduleClass.getName() + "' due to missing class: " + className
            );
            return false;
        }
    }
}
