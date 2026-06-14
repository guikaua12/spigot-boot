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
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

/**
 * Callback interface for contributing {@link BeanPostProcessor} instances to the framework.
 * <p>
 * Provide beans of this type via {@code @Configuration} classes with {@code @Bean} methods to
 * register custom post-processors during context initialization. Customizers are applied in the
 * DEFINITIONS_READY phase, before bean instantiation begins. Implement {@link Ordered#getOrder()}
 * to control application order; lower values are applied first.
 *
 * @see BeanPostProcessorRegistry
 * @see BeanPostProcessor
 */
@FunctionalInterface
public interface BeanPostProcessorRegistryCustomizer extends Ordered {

    /**
     * Customize the given registry by registering post-processors.
     *
     * @param registry the registry to customize, not null
     */
    void customize(@NotNull BeanPostProcessorRegistry registry);
}
