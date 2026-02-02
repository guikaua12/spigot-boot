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
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.exceptions.ModuleInitializationException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ModuleRegistry {
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
        DependencyManager dm = context.getBean(DependencyManager.class);
        ConditionContext conditionContext = new SimpleConditionContext(
                dm.getBeanDefinitionRegistry(),
                dm.getBeanInstanceRegistry(),
                moduleClass.getClassLoader()
        );

        if (ConditionEvaluator.shouldSkip(moduleClass, conditionContext, "ModuleRegistry")) {
            return;
        }

        if (componentRegistry.getComponentsAnnotations()
                .stream()
                .anyMatch(moduleClass::isAnnotationPresent)) {
            throw new IllegalStateException("Stereotype annotations are not allowed on module classes.");
        }

        context.registerBean(moduleClass);

        Module module = context.getBean(moduleClass);

        if (module == null) {
            throw new IllegalStateException(
                    "Failed to resolve module bean for '" + moduleClass.getName() + "' after registration. " +
                            "Context.getBean(...) returned null, so the module cannot be initialized."
            );
        }

        module.onInitialize(context);
    }
}
