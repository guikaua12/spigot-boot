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

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.exceptions.ModuleInitializationException;

import java.util.List;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ModuleRegistry {
    private final Logger logger;
    private final ComponentRegistry componentRegistry;

    public void initializeModules(@NotNull Context context, @NotNull List<Class<? extends Module>> modulesToLoad) {
        for (Class<? extends Module> moduleClass : modulesToLoad) {
            try {
                initializeModule(moduleClass, context);
            } catch (Exception e) {
                throw new ModuleInitializationException("Failed to load module '" + moduleClass.getName() + "'", e);
            }
        }
    }

    private void initializeModule(Class<? extends Module> moduleClass, Context context) throws Exception {
        if (!verifyModuleDependencies(moduleClass)) {
            return;
        }

        if (componentRegistry.getComponentsAnnotations()
                .stream()
                .anyMatch(moduleClass::isAnnotationPresent)) {
            throw new IllegalStateException("Stereotype annotations are not allowed on module classes.");
        }

        context.registerBean(moduleClass);

        Module module = context.getBean(moduleClass);

        module.onInitialize(context);
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
