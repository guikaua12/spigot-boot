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
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeanPostProcessorRegistryTest {

    private static BeanPostProcessor noop(int order) {
        return new BeanPostProcessor() {
            @Override
            public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                               @NotNull Object instance,
                                               @NotNull DependencyManager dependencyManager) {
                return instance;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @Test
    void getBeanPostProcessorRegistry_registersAndSortsByOrder() {
        DependencyManager dm = new DependencyManager();
        BeanPostProcessorRegistry registry = dm.getBeanPostProcessorRegistry();

        BeanPostProcessor high = noop(100);
        BeanPostProcessor low = noop(-100);
        registry.register(high);
        registry.register(low);

        List<BeanPostProcessor> processors = dm.getBeanPostProcessors();
        // the constructor already registers MethodHandlerProxyBeanPostProcessor (order 0)
        assertTrue(processors.contains(high));
        assertTrue(processors.contains(low));
        // sorted ascending: low(-100) must come before high(100)
        assertTrue(processors.indexOf(low) < processors.indexOf(high));
    }

    @Test
    void register_rejectsNull() {
        DependencyManager dm = new DependencyManager();
        assertThrows(NullPointerException.class, () -> dm.getBeanPostProcessorRegistry().register(null));
    }
}
