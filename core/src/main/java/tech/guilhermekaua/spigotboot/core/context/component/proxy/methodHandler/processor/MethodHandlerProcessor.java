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
package tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor;

import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class MethodHandlerProcessor {
    private DiscoveryIndexReader discoveryIndexReader;

    public List<RegisteredMethodHandler> processFromPackage(String basePackage, DependencyManager dependencyManager) {
        LinkedHashSet<Class<?>> handlerClasses = new LinkedHashSet<>(
                ReflectionUtils.getClassesAnnotatedWith(basePackage, RegisterMethodHandler.class));
        DiscoveryIndexReader reader = getDiscoveryIndexReader();
        if (reader.hasAnyIndex()) {
            handlerClasses.addAll(reader.classesInCategory(DiscoveryCategories.METHOD_HANDLER, basePackage));
        }

        return handlerClasses.stream()
                .sorted(Comparator.comparing(Class::getName))
                .flatMap(clazz -> processClass(clazz, dependencyManager).stream())
                .collect(Collectors.toList());
    }

    private DiscoveryIndexReader getDiscoveryIndexReader() {
        if (discoveryIndexReader == null) {
            discoveryIndexReader = DiscoveryIndexReader.create();
        }
        return discoveryIndexReader;
    }

    public List<RegisteredMethodHandler> processClass(Class<?> clazz, DependencyManager dependencyManager) {
        try {
            Object handler = dependencyManager.resolveDependency(clazz, BeanUtils.getQualifier(clazz));

            return Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(MethodHandler.class))
                    .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0] == MethodHandlerContext.class)
                    .sorted(Comparator.comparing(Method::toGenericString))
                    .map(method -> {
                        MethodHandler annotation = method.getAnnotation(MethodHandler.class);
                        return new RegisteredMethodHandler(
                                context -> method.invoke(handler, context),
                                annotation.targetClass(),
                                annotation.classAnnotatedWith(),
                                annotation.methodAnnotatedWith(),
                                annotation.order()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process handler: " + clazz.getName(), e);
        }
    }
}
