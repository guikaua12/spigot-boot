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
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

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

    @SuppressWarnings("unchecked")
    public void processClass(Class<?> clazz) {
        try {
            Object configObject = dependencyManager.resolveDependency(clazz, BeanUtils.getQualifier(clazz));

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;

                if (!Object.class.isAssignableFrom(method.getReturnType())) {
                    throw new RuntimeException("Method '" + method.getName() + "' in class '" + clazz.getName() + "' must return an Object type.");
                }

                try {
                    Object[] parameterDependencies = Arrays.stream(method.getParameters())
                            .map(param -> dependencyManager.resolveDependency(param.getType(), BeanUtils.getQualifier(param)))
                            .toArray();

                    Object beanInstance = method.invoke(configObject, parameterDependencies);
                    dependencyManager.registerDependency((Class<Object>) method.getReturnType(), beanInstance, BeanUtils.getQualifier(method), BeanUtils.getIsPrimary(method));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to process configuration class: '" + clazz.getName() + "'", t);
        }
    }
}
