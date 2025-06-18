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
package me.approximations.spigotboot.core.module;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.context.component.ComponentManager;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import me.approximations.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.module.annotations.ConditionalOnClass;
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
