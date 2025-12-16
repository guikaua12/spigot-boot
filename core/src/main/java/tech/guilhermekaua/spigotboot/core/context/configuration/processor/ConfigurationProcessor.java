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
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.configuration.proxy.ConfigurationClassProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class ConfigurationProcessor {

    public void processFromPackage(String basePackage, DependencyManager dependencyManager) {
        for (Class<?> configClass : ReflectionUtils.getClassesAnnotatedWith(basePackage, Configuration.class)) {
            processClass(configClass, dependencyManager);
        }
    }

    @SuppressWarnings("unchecked")
    public void processClass(Class<?> clazz, DependencyManager dependencyManager) {
        try {
            Object realConfigObject = dependencyManager.createInstance(clazz);

            Set<Method> beanMethods = collectBeanMethods(clazz);

            for (Method method : beanMethods) {
                registerBeanMethod(method, realConfigObject, dependencyManager);
            }

            Object configProxy = ConfigurationClassProxy.createProxy(
                    clazz,
                    realConfigObject,
                    beanMethods,
                    dependencyManager
            );

            dependencyManager.registerDependency(
                    (Class<Object>) clazz,
                    configProxy,
                    BeanUtils.getQualifier(clazz),
                    BeanUtils.getIsPrimary(clazz)
            );

        } catch (Throwable t) {
            throw new RuntimeException("Failed to process configuration class: '" + clazz.getName() + "'", t);
        }
    }

    private Constructor<?> findInjectConstructor(Class<?> clazz) {
        List<Constructor<?>> annotatedCtors = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(ctor -> ctor.isAnnotationPresent(Inject.class))
                .collect(Collectors.toList());

        if (annotatedCtors.size() == 1) {
            return annotatedCtors.get(0);
        }

        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        if (ctors.length == 1) {
            return ctors[0];
        }

        return null;
    }

    private Set<Method> collectBeanMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private void registerBeanMethod(Method method, Object realConfigObject, DependencyManager dependencyManager) {
        Class<?> returnType = method.getReturnType();

        if (!Object.class.isAssignableFrom(returnType)) {
            throw new RuntimeException("Method '" + method.getName() + "' in class '" +
                    method.getDeclaringClass().getName() + "' must return an Object type.");
        }

        String qualifier = BeanUtils.getQualifier(method);
        boolean isPrimary = BeanUtils.getIsPrimary(method);

        dependencyManager.registerDependency(
                (Class<Object>) returnType,
                qualifier,
                isPrimary,
                (type) -> {
                    try {
                        Object[] parameterDependencies = Arrays.stream(method.getParameters())
                                .map(param -> dependencyManager.resolveDependency(
                                        param.getType(),
                                        BeanUtils.getQualifier(param)
                                ))
                                .toArray();

                        method.setAccessible(true);
                        return method.invoke(realConfigObject, parameterDependencies);
                    } catch (Throwable t) {
                        throw new RuntimeException("Failed to invoke @Bean method: " + method.getName(), t);
                    }
                });
    }
}
