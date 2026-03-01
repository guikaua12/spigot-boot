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

import javassist.util.proxy.ProxyObject;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.reflect.Modifier;
import java.util.Objects;

public class MethodHandlerProxyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                       @NotNull Object instance,
                                       @NotNull DependencyManager dependencyManager) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        Objects.requireNonNull(dependencyManager, "dependencyManager cannot be null");

        if (instance instanceof ProxyObject) {
            return instance;
        }

        final boolean shouldProxy;
        try {
            shouldProxy = dependencyManager.getBeanProxyDeciderResolver().shouldProxy(definition, dependencyManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve proxying for bean: " + definition.identifier(), e);
        }

        if (!shouldProxy) {
            return instance;
        }

        Class<?> proxyType = resolveProxyType(definition, instance);
        if (!proxyType.isInterface() && Modifier.isFinal(proxyType.getModifiers())) {
            throw new IllegalStateException("Cannot proxy final class: " + proxyType.getName());
        }

        @SuppressWarnings("unchecked")
        Class<Object> castedType = (Class<Object>) proxyType;
        return ComponentProxy.createProxy(castedType, instance, new Class[0], new Object[0]);
    }

    private Class<?> resolveProxyType(@NotNull BeanDefinition definition, @NotNull Object instance) {
        Class<?> instanceType = instance.getClass();
        if (!Modifier.isFinal(instanceType.getModifiers())) {
            return instanceType;
        }

        Class<?> definitionType = definition.getType();
        if (definitionType != null && definitionType.isInterface() && definitionType.isAssignableFrom(instanceType)) {
            return definitionType;
        }

        return instanceType;
    }
}
