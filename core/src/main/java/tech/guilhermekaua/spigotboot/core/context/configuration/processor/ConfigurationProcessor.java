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
package tech.guilhermekaua.spigotboot.core.context.configuration.processor;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnEnable;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.configuration.proxy.ConfigurationClassProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ConfigurationProcessor {
    private DiscoveryIndexReader discoveryIndexReader;

    public void processFromPackage(String basePackage, DependencyManager dependencyManager) {
        LinkedHashSet<Class<?>> configClasses = new LinkedHashSet<>(
                ReflectionUtils.getClassesAnnotatedWith(basePackage, Configuration.class));
        DiscoveryIndexReader reader = getDiscoveryIndexReader();
        if (reader.hasAnyIndex()) {
            configClasses.addAll(reader.classesInCategory(DiscoveryCategories.CONFIGURATION, basePackage));
        }

        for (Class<?> configClass : configClasses) {
            processClass(configClass, dependencyManager);
        }
    }

    private DiscoveryIndexReader getDiscoveryIndexReader() {
        if (discoveryIndexReader == null) {
            discoveryIndexReader = DiscoveryIndexReader.create();
        }
        return discoveryIndexReader;
    }

    @SuppressWarnings("unchecked")
    public void processClass(Class<?> clazz, DependencyManager dependencyManager) {
        try {
            ConditionContext conditionContext = new SimpleConditionContext(
                    dependencyManager.getBeanDefinitionRegistry(),
                    null,
                    Thread.currentThread().getContextClassLoader()
            );

            if (ConditionEvaluator.shouldSkip(clazz, conditionContext, "ConfigurationProcessor")) {
                return;
            }

            Set<Method> beanMethods = collectBeanMethods(clazz);

            Object configProxy = ConfigurationClassProxy.createProxy(
                    clazz,
                    beanMethods,
                    dependencyManager
            );

            dependencyManager.registerDependency(
                    (Class<Object>) clazz,
                    configProxy,
                    BeanUtils.getQualifier(clazz),
                    BeanUtils.getIsPrimary(clazz)
            );

            for (Method method : beanMethods) {
                registerBeanMethod(method, configProxy, dependencyManager);
            }

        } catch (Throwable t) {
            throw new RuntimeException("Failed to process configuration class: '" + clazz.getName() + "'", t);
        }
    }

    private Set<Method> collectBeanMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private void registerBeanMethod(Method method, Object configProxy, DependencyManager dependencyManager) {
        validateBeanMethodLifecycleUsage(method);

        ConditionContext conditionContext = new SimpleConditionContext(
                dependencyManager.getBeanDefinitionRegistry(),
                null,
                Thread.currentThread().getContextClassLoader()
        );

        if (ConditionEvaluator.shouldSkip(method, conditionContext, "ConfigurationProcessor")) {
            return;
        }

        Class<?> returnType = method.getReturnType();

        if (!Object.class.isAssignableFrom(returnType)) {
            throw new RuntimeException("Method '" + method.getName() + "' in class '" +
                    method.getDeclaringClass().getName() + "' must return an Object type.");
        }

        String qualifier = BeanUtils.getQualifier(method);
        if (qualifier == null || qualifier.trim().isEmpty()) {
            qualifier = method.getName();
        }
        boolean isPrimary = BeanUtils.getIsPrimary(method);

        dependencyManager.registerDependency(
                (Class<Object>) returnType,
                qualifier,
                isPrimary,
                (type) -> {
                    try {
                        method.setAccessible(true);
                        return method.invoke(configProxy, dependencyManager.resolveArguments(method));
                    } catch (Throwable t) {
                        throw new RuntimeException("Failed to invoke @Bean method: " + method.getName(), t);
                    }
                });
    }

    private void validateBeanMethodLifecycleUsage(Method method) {
        if (method.isAnnotationPresent(OnEnable.class)) {
            throw new IllegalStateException(String.format(
                    "Method '%s' in class '%s' cannot be annotated with both @Bean and @OnEnable.",
                    method.getName(),
                    method.getDeclaringClass().getName()
            ));
        }

        if (method.isAnnotationPresent(OnDisable.class)) {
            throw new IllegalStateException(String.format(
                    "Method '%s' in class '%s' cannot be annotated with both @Bean and @OnDisable.",
                    method.getName(),
                    method.getDeclaringClass().getName()
            ));
        }
    }
}
