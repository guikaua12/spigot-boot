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
package me.approximations.spigotboot.core.context.configuration.processor;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.context.configuration.annotations.Bean;
import me.approximations.spigotboot.core.context.configuration.annotations.Configuration;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ConfigurationProcessor {
    private final DependencyManager dependencyManager;

    public void processFromPackage(Class<?>... clazz) {
        for (Class<?> baseClass : clazz) {
            for (Class<?> configClass : ReflectionUtils.getClassesAnnotatedWith(baseClass, Configuration.class)) {
                processClass(configClass);
            }
        }
    }

    public void processClass(Class<?> clazz) {
        try {
            dependencyManager.registerDependency(clazz);
            Object configObject = dependencyManager.resolveDependency(clazz);

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;

                if (!Object.class.isAssignableFrom(method.getReturnType())) {
                    throw new RuntimeException("Method '" + method.getName() + "' in class '" + clazz.getName() + "' must return an Object type.");
                }

                try {
                    Object[] parameterDependencies = Arrays.stream(method.getParameterTypes())
                            .map(dependencyManager::resolveDependency)
                            .toArray();

                    Object beanInstance = method.invoke(configObject, parameterDependencies);
                    dependencyManager.registerDependency(method.getReturnType(), beanInstance);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to process configuration class: '" + clazz.getName() + "'", t);
        }
    }
}
