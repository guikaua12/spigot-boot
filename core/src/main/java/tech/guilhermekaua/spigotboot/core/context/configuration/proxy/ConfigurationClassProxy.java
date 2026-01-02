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
import javassist.util.proxy.ProxyObject;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanInstanceRegistry;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ConfigurationClassProxy implements MethodHandler {
    private final DependencyManager dependencyManager;
    private final Set<Method> beanMethods;

    public ConfigurationClassProxy(DependencyManager dependencyManager, Set<Method> beanMethods) {
        this.dependencyManager = dependencyManager;
        this.beanMethods = beanMethods;
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(@NotNull Class<T> clazz,
                                    @NotNull Set<Method> beanMethods,
                                    @NotNull DependencyManager dependencyManager) {
        Objects.requireNonNull(clazz, "clazz cannot be null");
        Objects.requireNonNull(beanMethods, "beanMethods cannot be null");
        Objects.requireNonNull(dependencyManager, "dependencyManager cannot be null");

        try {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(clazz);

            Class<?> proxyClass = factory.createClass();

            Constructor<?> ctor = dependencyManager.findInjectConstructor(clazz);
            if (ctor == null) {
                throw new IllegalStateException("No injectable constructor found for configuration class: " + clazz.getName());
            }

            Object[] ctorArgs = dependencyManager.resolveArguments(ctor);

            Constructor<?> proxyCtor = proxyClass.getDeclaredConstructor(ctor.getParameterTypes());
            proxyCtor.setAccessible(true);

            T proxy = (T) proxyCtor.newInstance(ctorArgs);
            dependencyManager.injectDependencies(clazz, proxy);

            ((ProxyObject) proxy)
                    .setHandler(new ConfigurationClassProxy(dependencyManager, beanMethods));

            return proxy;
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
                return handleBeanMethodInvocation(self, beanMethod, proceed, args);
            }
        }

        if (proceed == null) {
            throw new IllegalStateException("No proceed method available for: " + thisMethod);
        }

        proceed.setAccessible(true);
        return proceed.invoke(self, args);
    }

    private Object handleBeanMethodInvocation(Object self, Method beanMethod, Method proceed, Object[] args)
            throws Throwable {
        Class<?> returnType = beanMethod.getReturnType();
        String qualifier = BeanUtils.getQualifier(beanMethod);
        if (qualifier == null || qualifier.trim().isEmpty()) {
            qualifier = beanMethod.getName();
        }

        BeanDefinition definition = findBeanDefinition(returnType, qualifier);
        if (definition == null) {
            throw new IllegalStateException("BeanDefinition not found for @Bean method: " + beanMethod.getName() +
                    " returning " + returnType.getName() + " with qualifier '" + qualifier + "'");
        }

        BeanInstanceRegistry instanceRegistry = dependencyManager.getBeanInstanceRegistry();
        if (instanceRegistry.contains(definition)) {
            return instanceRegistry.get(definition);
        }

        Object[] parameterDependencies = dependencyManager.resolveArguments(beanMethod);

        // invoke the actual method via proceed (bypasses proxy interception for this call).
        // 'self' is still the proxy, so internal calls to other @Bean methods will be intercepted
        Object result = proceed.invoke(self, parameterDependencies);

        instanceRegistry.put(definition, result);

        return result;
    }

    private BeanDefinition findBeanDefinition(Class<?> returnType, String qualifier) {
        List<BeanDefinition> definitions = dependencyManager.getBeanDefinitionRegistry().getDefinitions(returnType);

        for (BeanDefinition def : definitions) {
            if (Objects.equals(def.getQualifierName(), qualifier)) {
                return def;
            }
        }

        return null;
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
