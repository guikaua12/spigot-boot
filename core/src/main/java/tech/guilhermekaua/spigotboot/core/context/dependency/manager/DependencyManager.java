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
package tech.guilhermekaua.spigotboot.core.context.dependency.manager;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.Dependency;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.core.exceptions.MultipleConstructorException;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

// Context.initialize -> Context.scan -> DependencyManager.registerDependency -> Context.scan -> DependencyManager.resolveDependency

@RequiredArgsConstructor
public class DependencyManager {
    @Getter
    private final Map<Class<?>, List<Dependency>> dependencyMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T resolveDependency(@NotNull Class<T> clazz, @Nullable String qualifier) {
        try {
            Objects.requireNonNull(clazz, "class cannot be null.");

            List<Dependency> dependencies = dependencyMap.computeIfAbsent(clazz, k -> new ArrayList<>());

            if (dependencies.isEmpty()) {
                return null;
            }

            if (qualifier != null) {
                Dependency dependency = dependencies.stream().filter(dep -> qualifier.equals(dep.getQualifierName())).findFirst().orElse(null);

                if (dependency == null) {
                    return null;
                }

                if (dependency.getInstance() != null) {
                    return clazz.cast(dependency.getInstance());
                }

                if (dependency.getResolver() != null) {
                    T instance = (T) dependency.getResolver().resolve(clazz);
                    dependency.setInstance(instance);
                    return instance;
                }

                return (T) createInstance(dependency.getType());
            }

            if (dependencies.size() == 1) {
                Dependency singleDependency = dependencies.get(0);
                if (singleDependency.getInstance() != null) {
                    return clazz.cast(singleDependency.getInstance());
                }

                if (singleDependency.getResolver() != null) {
                    T instance = (T) singleDependency.getResolver().resolve(clazz);
                    singleDependency.setInstance(instance);
                    return instance;
                }

                T instance = (T) createInstance(singleDependency.getType());
                singleDependency.setInstance(instance);
                return instance;
            }

            List<Dependency> primary = dependencies.stream().filter(Dependency::isPrimary).collect(Collectors.toList());

            if (primary.isEmpty()) {
                throw new IllegalStateException(String.format("No primary dependency found for class %s and qualifier %s", clazz.getSimpleName(), qualifier));
            }

            if (primary.size() > 1) {
                throw new IllegalStateException(String.format("Multiple primary dependencies found for class %s and qualifier %s", clazz.getSimpleName(), qualifier));
            }

            Dependency primaryDependency = primary.get(0);
            if (primaryDependency.getInstance() != null) {
                return clazz.cast(primaryDependency.getInstance());
            }

            if (primaryDependency.getResolver() != null) {
                T instance = (T) primaryDependency.getResolver().resolve(clazz);
                primaryDependency.setInstance(instance);
                return instance;
            }

            T instance = (T) createInstance(primaryDependency.getType());
            primaryDependency.setInstance(instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + clazz, e);
        }
    }

    public <T> T resolveDependency(@NotNull Class<T> clazz, @Nullable String qualifier, Supplier<T> fallback) {
        Objects.requireNonNull(clazz, "class cannot be null.");
        Objects.requireNonNull(fallback, "fallback supplier cannot be null.");

        T instance = resolveDependency(clazz, qualifier);
        if (instance != null) {
            return instance;
        }

        instance = fallback.get();
        if (instance == null) {
            throw new IllegalStateException("Fallback supplier returned null for class: " + clazz);
        }

        return registerDependency(instance, qualifier, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull T instance, @Nullable String qualifier, boolean primary) {
        Class<T> dependencyClass = (Class<T>) instance.getClass();

        for (Class<? super T> superInterface : ReflectionUtils.getSuperInterfaces(dependencyClass)) {
            registerDependency(superInterface, dependencyClass, instance, qualifier, primary, null);
        }

        return (T) registerDependency(dependencyClass, dependencyClass, instance, qualifier, primary, null).getInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> clazz, @NotNull T instance, @Nullable String qualifier, boolean primary) {
        return (T) registerDependency(clazz, (Class<? extends T>) instance.getClass(), instance, qualifier, primary, null).getInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> dependencyClass, @Nullable String qualifier, boolean primary, DependencyResolveResolver<T> resolver) {
        for (Class<? super T> superInterface : ReflectionUtils.getSuperInterfaces(dependencyClass)) {
            registerDependency((Class<T>) superInterface, dependencyClass, null, qualifier, primary, resolver);
        }

        return (T) registerDependency(dependencyClass, dependencyClass, null, qualifier, primary, resolver).getInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> clazz, @NotNull Class<? extends T> dependencyClass, @Nullable String qualifier, boolean primary, @Nullable DependencyResolveResolver<T> resolver) {
        return (T) registerDependency(clazz, dependencyClass, null, qualifier, primary, resolver).getInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> clazz, @NotNull Class<? extends T> dependencyClass, @Nullable String qualifier, boolean primary) {
        return registerDependency(clazz, dependencyClass, qualifier, primary, null);
    }

    public <T> Dependency registerDependency(@NotNull Class<T> clazz, @NotNull Class<? extends T> dependencyClass, @Nullable T instance, @Nullable String qualifier, boolean primary, @Nullable DependencyResolveResolver<T> resolver) {
        try {
            Objects.requireNonNull(clazz, "clazz cannot be null.");
            Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");
            Preconditions.checkArgument(!(dependencyClass.isInterface() && resolver == null), "You cannot register an interface without a resolver. Use DependencyResolveResolver to provide an implementation.");

            List<Dependency> dependencies = dependencyMap.computeIfAbsent(clazz, k -> new ArrayList<>());
            boolean duplicateDependency = dependencies.stream()
                    .anyMatch(dependency ->
                            dependency.getType().equals(dependencyClass) &&
                                    ((qualifier != null && qualifier.equals(dependency.getQualifierName())) ||
                                            (qualifier == null && dependency.getQualifierName() == null))
                    );

            if (duplicateDependency) {
                throw new IllegalStateException(
                        "Dependency with qualifier '" + qualifier + "' already exists for class: " + clazz
                );
            }


            Dependency dependency = new Dependency(dependencyClass, qualifier, primary, instance, resolver);
            dependencies.add(dependency);

            return dependency;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register dependency using (" + clazz + " -> " + dependencyClass + "): ", e);
        }
    }

    private <T> T createInstance(Class<T> type) throws Exception {
        Constructor<?> ctor = findInjectConstructor(type);

        if (ctor == null) {
            return null;
        }

        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolveDependency(paramTypes[i], null);
        }

        ctor.setAccessible(true);
        T instance = type.cast(ctor.newInstance(params));
        injectDependencies(instance);
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> void injectDependencies(@NotNull T instance) {
        Objects.requireNonNull(instance, "instance cannot be null.");

        Class<T> type = (Class<T>) instance.getClass();

        try {
            setterInject(type, instance);
            fieldInject(type, instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to inject dependencies for: " + type.getName(), e);
        }
    }

    private @Nullable Constructor<?> findInjectConstructor(@NotNull Class<?> type) {
        Objects.requireNonNull(type, "type cannot be null.");

        if (type.isInterface()) {
            return null;
        }

        List<Constructor<?>> annotatedCtors = Arrays.stream(type.getDeclaredConstructors())
                .filter(ctor -> ctor.isAnnotationPresent(Inject.class))
                .collect(Collectors.toList());

        if (annotatedCtors.size() > 1) {
            throw new MultipleConstructorException(String.format(
                    "Multiple constructors annotated with @Inject found for class %s. Please use @Inject on only one constructor.",
                    type.getSimpleName()
            ));
        }

        if (!annotatedCtors.isEmpty()) {
            return annotatedCtors.get(0);
        }

        Constructor<?>[] ctors = type.getDeclaredConstructors();
        if (ctors.length > 1) {
            throw new MultipleConstructorException(String.format(
                    "Multiple constructors found for class %s. Please use @Inject annotation to specify which constructor to use.",
                    type.getSimpleName()
            ));
        }

        return ctors[0];
    }

    private <T> void setterInject(@NotNull Class<T> type, @NotNull T instance) throws IllegalAccessException, InvocationTargetException {
        Objects.requireNonNull(type, "type cannot be null.");
        Objects.requireNonNull(instance, "instance cannot be null.");

        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                Class<?> depType = method.getParameterTypes()[0];
                Object dep = resolveDependency(depType, BeanUtils.getQualifier(method));
                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
    }

    private <T> void fieldInject(@NotNull Class<T> type, @NotNull T instance) throws IllegalAccessException {
        Objects.requireNonNull(type, "type cannot be null.");
        Objects.requireNonNull(instance, "instance cannot be null.");

        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object dep = resolveDependency(field.getType(), BeanUtils.getQualifier(field));
                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
    }
}