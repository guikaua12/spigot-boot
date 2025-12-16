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
package tech.guilhermekaua.spigotboot.core.context.configuration.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;

public class ConfigurationClassProxy implements MethodHandler {
    private final Object realConfigObject;
    private final DependencyManager dependencyManager;
    private final Set<Method> beanMethods;

    public ConfigurationClassProxy(Object realConfigObject, DependencyManager dependencyManager,
                                   Set<Method> beanMethods) {
        this.realConfigObject = realConfigObject;
        this.dependencyManager = dependencyManager;
        this.beanMethods = beanMethods;
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(@NotNull Class<T> clazz,
                                    @NotNull Object realConfigObject,
                                    @NotNull Set<Method> beanMethods,
                                    @NotNull DependencyManager dependencyManager) {
        Objects.requireNonNull(clazz, "clazz cannot be null");
        Objects.requireNonNull(realConfigObject, "realConfigObject cannot be null");
        Objects.requireNonNull(beanMethods, "beanMethods cannot be null");
        Objects.requireNonNull(dependencyManager, "dependencyManager cannot be null");

        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(clazz);

        try {
            return (T) factory.create(
                    new Class<?>[0],
                    new Object[0],
                    new ConfigurationClassProxy(realConfigObject, dependencyManager, beanMethods));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create configuration proxy for " + clazz.getName(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (thisMethod.getName().equals("toString")) {
            return self.getClass().getSimpleName() + "@" + Integer.toHexString(self.hashCode())
                    + " (Configuration Proxy)";
        }

        for (Method beanMethod : beanMethods) {
            if (methodsMatch(thisMethod, beanMethod)) {
                return dependencyManager.resolveDependency(
                        thisMethod.getReturnType(),
                        BeanUtils.getQualifier(beanMethod)
                );
            }
        }

        thisMethod.setAccessible(true);
        return thisMethod.invoke(realConfigObject, args);
    }

    private boolean methodsMatch(Method m1, Method m2) {
        if (!m1.getName().equals(m2.getName())) {
            return false;
        }

        Class<?>[] params1 = m1.getParameterTypes();
        Class<?>[] params2 = m2.getParameterTypes();

        if (params1.length != params2.length) {
            return false;
        }

        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].equals(params2[i])) {
                return false;
            }
        }

        return true;
    }
}
