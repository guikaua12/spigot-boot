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
package me.approximations.spigotboot.core.context.component.proxy.methodHandler.processor;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations.RegisterMethodHandler;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.utils.ReflectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MethodHandlerProcessor {
    private final DependencyManager dependencyManager;

    public List<RegisteredMethodHandler> processFromPackage(Class<?>... bases) {
        return Arrays.stream(bases)
                .map(base -> ReflectionUtils.getClassesAnnotatedWith(base, RegisterMethodHandler.class))
                .flatMap(Set::stream)
                .flatMap(clazz -> processClass(clazz).stream())
                .collect(Collectors.toList());
    }

    private List<RegisteredMethodHandler> processClass(Class<?> clazz) {
        try {
            dependencyManager.registerDependency(clazz);
            Object handler = dependencyManager.resolveDependency(clazz);

            return Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(MethodHandler.class))
                    .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0] == MethodHandlerContext.class)
                    .map(method -> {
                        MethodHandler annotation = method.getAnnotation(MethodHandler.class);
                        return new RegisteredMethodHandler(
                                context -> method.invoke(handler, context),
                                annotation.targetClass(),
                                annotation.classAnnotatedWith(),
                                annotation.methodAnnotatedWith()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process handler: " + clazz.getName(), e);
        }
    }
}
