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
package tech.guilhermekaua.spigotboot.core.context.condition;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanInstanceRegistry;

import java.util.Objects;

@Getter
public class SimpleConditionContext implements ConditionContext {
    private final BeanDefinitionRegistry beanDefinitionRegistry;
    private final BeanInstanceRegistry beanInstanceRegistry;
    private final ClassLoader classLoader;

    public SimpleConditionContext(@NotNull BeanDefinitionRegistry beanDefinitionRegistry,
                                  @Nullable BeanInstanceRegistry beanInstanceRegistry,
                                  @NotNull ClassLoader classLoader) {
        this.beanDefinitionRegistry = Objects.requireNonNull(beanDefinitionRegistry, "beanDefinitionRegistry cannot be null");
        this.beanInstanceRegistry = beanInstanceRegistry;
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    }
}
