/*
 * The MIT License
 * Copyright © 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.core.context.dependency.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.BeanDefinitionsReadyListener;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory component that applies {@link CustomInjectorRegistryCustomizer} beans to the
 * framework's {@link CustomInjectorRegistry} during context initialization.
 * <p>
 * This factory runs in the DEFINITIONS_READY phase, after all bean definitions have been
 * discovered but before instantiation begins. This ensures custom injectors are registered
 * and available for normal dependency injection.
 * <p>
 * The factory:
 * <ol>
 *   <li>Obtains the framework's registry instance from the {@link DependencyManager}</li>
 *   <li>Resolves all {@link CustomInjectorRegistryCustomizer} beans</li>
 *   <li>Sorts customizers by their {@link Ordered#getOrder()} value</li>
 *   <li>Applies each customizer to the registry in order</li>
 * </ol>
 *
 * @see CustomInjectorRegistryCustomizer
 * @see CustomInjectorRegistry
 */
@Component
public class CustomInjectorRegistryFactory implements BeanDefinitionsReadyListener, Ordered {

    /**
     * Order value ensuring this factory runs early in the DEFINITIONS_READY phase.
     * Uses a negative value so internal framework setup occurs before user listeners.
     */
    private static final int ORDER = -1000;

    @Override
    public void onBeanDefinitionsReady(@NotNull Context context,
                                       @NotNull BeanDefinitionRegistry definitionRegistry,
                                       @NotNull BeanRegistrar registrar) {
        DependencyManager dependencyManager = context.getDependencyManager();
        CustomInjectorRegistry registry = dependencyManager.getCustomInjectorRegistry();
        Logger logger = context.getPlugin().getLogger();

        List<CustomInjectorRegistryCustomizer> customizers = resolveCustomizers(dependencyManager, definitionRegistry, logger);
        if (customizers.isEmpty()) {
            return;
        }

        sortByOrder(customizers);
        applyCustomizers(customizers, registry, logger);
    }

    private @NotNull List<CustomInjectorRegistryCustomizer> resolveCustomizers(
            @NotNull DependencyManager dependencyManager,
            @NotNull BeanDefinitionRegistry definitionRegistry,
            @NotNull Logger logger) {
        List<BeanDefinition> definitions = definitionRegistry.getDefinitions(CustomInjectorRegistryCustomizer.class);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<CustomInjectorRegistryCustomizer> customizers = new ArrayList<>(definitions.size());
        for (BeanDefinition definition : definitions) {
            try {
                CustomInjectorRegistryCustomizer customizer = dependencyManager.resolveDependency(
                        CustomInjectorRegistryCustomizer.class,
                        definition.getQualifierName()
                );
                if (customizer != null) {
                    customizers.add(customizer);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to resolve CustomInjectorRegistryCustomizer: " + definition.identifier(), e);
            }
        }
        return customizers;
    }

    private void sortByOrder(@NotNull List<CustomInjectorRegistryCustomizer> customizers) {
        customizers.sort(Comparator.comparingInt(Ordered::getOrder));
    }

    private void applyCustomizers(@NotNull List<CustomInjectorRegistryCustomizer> customizers,
                                  @NotNull CustomInjectorRegistry registry,
                                  @NotNull Logger logger) {
        for (CustomInjectorRegistryCustomizer customizer : customizers) {
            try {
                customizer.customize(registry);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying CustomInjectorRegistryCustomizer: " + customizer.getClass().getName(), e);
            }
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
