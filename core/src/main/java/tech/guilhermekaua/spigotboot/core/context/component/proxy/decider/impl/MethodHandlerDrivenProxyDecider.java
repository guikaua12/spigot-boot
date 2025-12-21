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
package tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.impl;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.BeanProxyDecider;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Default proxy decider: proxies a bean if at least one registered {@link RegisteredMethodHandler}
 * could ever match it (based on the handler's metadata).
 */
@Component
public class MethodHandlerDrivenProxyDecider implements BeanProxyDecider {
    @Override
    public boolean shouldProxy(@NotNull BeanDefinition definition, @NotNull DependencyManager dependencyManager) {
        Class<?> beanClass = definition.getType();
        if (beanClass == null) {
            return false;
        }

        for (RegisteredMethodHandler handler : MethodHandlerRegistry.getAllHandlers()) {
            if (handlerCouldApply(handler, beanClass)) {
                return true;
            }
        }

        return false;
    }

    private boolean handlerCouldApply(RegisteredMethodHandler handler, Class<?> beanClass) {
        Class<?> targetClass = handler.getTargetClass();
        if (targetClass != null && !targetClass.isAssignableFrom(beanClass)) {
            return false;
        }

        Class<? extends Annotation> classAnn = handler.getClassTargetAnnotation();
        if (classAnn != null && !beanClass.isAnnotationPresent(classAnn)) {
            return false;
        }

        Class<? extends Annotation> methodAnn = handler.getMethodTargetAnnotation();
        if (methodAnn != null && !hasAnyMethodAnnotated(beanClass, methodAnn)) {
            return false;
        }

        return true;
    }

    private boolean hasAnyMethodAnnotated(Class<?> beanClass, Class<? extends Annotation> annotation) {
        for (Method method : beanClass.getMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }

        for (Method method : beanClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }

        for (Class<?> iface : beanClass.getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.isAnnotationPresent(annotation)) {
                    return true;
                }
            }
        }

        return false;
    }
}


