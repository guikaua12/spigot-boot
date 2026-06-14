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
package tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor;

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
 * Applies {@link BeanPostProcessorRegistryCustomizer} beans to the framework's
 * {@link BeanPostProcessorRegistry} during the DEFINITIONS_READY phase, before instantiation begins,
 * so contributed post-processors run on every bean.
 *
 * @see BeanPostProcessorRegistryCustomizer
 */
@Component
public class BeanPostProcessorRegistryFactory implements BeanDefinitionsReadyListener, Ordered {

    private static final int ORDER = -1000;

    @Override
    public void onBeanDefinitionsReady(@NotNull Context context,
                                       @NotNull BeanDefinitionRegistry definitionRegistry,
                                       @NotNull BeanRegistrar registrar) {
        DependencyManager dependencyManager = context.getDependencyManager();
        BeanPostProcessorRegistry registry = dependencyManager.getBeanPostProcessorRegistry();
        Logger logger = context.getPlugin().getLogger();

        List<BeanPostProcessorRegistryCustomizer> customizers = resolveCustomizers(dependencyManager, definitionRegistry, logger);
        if (customizers.isEmpty()) {
            return;
        }

        customizers.sort(Comparator.comparingInt(Ordered::getOrder));
        applyCustomizers(customizers, registry, logger);
    }

    private @NotNull List<BeanPostProcessorRegistryCustomizer> resolveCustomizers(
            @NotNull DependencyManager dependencyManager,
            @NotNull BeanDefinitionRegistry definitionRegistry,
            @NotNull Logger logger) {
        List<BeanDefinition> definitions = definitionRegistry.getDefinitions(BeanPostProcessorRegistryCustomizer.class);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<BeanPostProcessorRegistryCustomizer> customizers = new ArrayList<>(definitions.size());
        for (BeanDefinition definition : definitions) {
            try {
                BeanPostProcessorRegistryCustomizer customizer = dependencyManager.resolveDependency(
                        BeanPostProcessorRegistryCustomizer.class,
                        definition.getQualifierName()
                );
                if (customizer != null) {
                    customizers.add(customizer);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to resolve BeanPostProcessorRegistryCustomizer: " + definition.identifier(), e);
            }
        }
        return customizers;
    }

    private void applyCustomizers(@NotNull List<BeanPostProcessorRegistryCustomizer> customizers,
                                  @NotNull BeanPostProcessorRegistry registry,
                                  @NotNull Logger logger) {
        for (BeanPostProcessorRegistryCustomizer customizer : customizers) {
            try {
                customizer.customize(registry);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying BeanPostProcessorRegistryCustomizer: " + customizer.getClass().getName(), e);
            }
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
