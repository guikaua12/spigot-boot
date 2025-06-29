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
package tech.guilhermekaua.spigotboot.core.module;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.GlobalContext;
import tech.guilhermekaua.spigotboot.core.context.PluginContext;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ModuleRegistry {
    private final ModuleDiscoveryService moduleDiscoveryService = new ModuleDiscoveryService();
    @Getter
    private final Map<Class<? extends Module>, Module> loadedModules = new HashMap<>();
    private final Logger logger;

    public void loadModules(GlobalContext globalContext) {
        for (Class<? extends Module> moduleClass : moduleDiscoveryService.discoverModules()) {
            try {
                loadModule(moduleClass, globalContext);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void initializeModules(PluginContext pluginContext) {
        for (Module module : loadedModules.values()) {
            try {
                initializeModule(module, pluginContext);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadModule(Class<? extends Module> moduleClass, GlobalContext globalContext) {
        try {
            if (!verifyModuleDependencies(moduleClass)) {
                return;
            }

            globalContext.scan(moduleClass.getPackage().getName());
            globalContext.registerBean(moduleClass);

            Module module = globalContext.getBean(moduleClass);
            module.onLoad(globalContext);

            loadedModules.put(moduleClass, module);
        } catch (Exception e) {
            loadedModules.remove(moduleClass);
            throw new RuntimeException("Failed to load module: '" + moduleClass.getName() + "'", e);
        }
    }

    private void initializeModule(Module module, PluginContext pluginContext) {
        try {
            module.onInitialize(pluginContext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load module: '" + module.getClass().getName() + "'", e);
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
            logger.info(
                    conditionalOnClass.message() != null ?
                            conditionalOnClass.message() :
                            "Skipping module '" + moduleClass.getName() + "' due to missing class: " + e.typeName()
            );
            return false;
        } catch (ClassNotFoundException e) {
            logger.info(
                    conditionalOnClass.message() != null ?
                            conditionalOnClass.message() :
                            "Skipping module '" + moduleClass.getName() + "' due to missing class: " + className
            );
            return false;
        }
    }
}
