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
package tech.guilhermekaua.spigotboot.core.test.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryFactory;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BeanPostProcessorRegistryFactoryTest {
    private DependencyManager dependencyManager;
    private BeanDefinitionRegistry definitionRegistry;
    private Context mockContext;
    private BeanRegistrar mockRegistrar;

    private static BeanPostProcessor noop(int order) {
        return new BeanPostProcessor() {
            @Override
            public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                               @NotNull Object instance,
                                               @NotNull DependencyManager dm) {
                return instance;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        definitionRegistry = dependencyManager.getBeanDefinitionRegistry();
        mockContext = mock(Context.class);
        mockRegistrar = mock(BeanRegistrar.class);

        BootPlugin plugin = mock(BootPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(mockContext.getDependencyManager()).thenReturn(dependencyManager);
        when(mockContext.getPlugin()).thenReturn(plugin);
    }

    @Test
    void appliesCustomizersInOrderAndRegistersProcessors() {
        List<String> callOrder = new ArrayList<>();
        BeanPostProcessor markerLow = noop(5);
        BeanPostProcessor markerHigh = noop(6);

        BeanPostProcessorRegistryCustomizer first = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                callOrder.add("first");
                registry.register(markerLow);
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };
        BeanPostProcessorRegistryCustomizer second = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                callOrder.add("second");
                registry.register(markerHigh);
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(second, "second", false);
        dependencyManager.registerDependency(first, "first", false);

        new BeanPostProcessorRegistryFactory().onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);

        assertEquals(List.of("first", "second"), callOrder);
        assertTrue(dependencyManager.getBeanPostProcessors().contains(markerLow));
        assertTrue(dependencyManager.getBeanPostProcessors().contains(markerHigh));
    }

    @Test
    void continuesWhenCustomizerThrows() {
        BeanPostProcessor marker = noop(7);
        BeanPostProcessorRegistryCustomizer failing = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                throw new RuntimeException("boom");
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };
        BeanPostProcessorRegistryCustomizer succeeding = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                registry.register(marker);
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(failing, "failing", false);
        dependencyManager.registerDependency(succeeding, "succeeding", false);

        BeanPostProcessorRegistryFactory factory = new BeanPostProcessorRegistryFactory();
        assertDoesNotThrow(() -> factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar));
        assertTrue(dependencyManager.getBeanPostProcessors().contains(marker));
    }

    @Test
    void emptyIsNoOp() {
        int before = dependencyManager.getBeanPostProcessors().size();
        new BeanPostProcessorRegistryFactory().onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);
        assertEquals(before, dependencyManager.getBeanPostProcessors().size());
    }
}
