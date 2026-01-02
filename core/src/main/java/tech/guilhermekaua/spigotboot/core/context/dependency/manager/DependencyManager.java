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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.strategy.BeanProxyDeciderResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanInstanceRegistry;
import tech.guilhermekaua.spigotboot.core.exceptions.MultipleConstructorException;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.CollectionTypeUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

// Context.initialize -> Context.scan -> DependencyManager.registerDependency -> Context.scan -> DependencyManager.resolveDependency
public class DependencyManager {
    @Getter
    private final BeanDefinitionRegistry beanDefinitionRegistry;

    @Getter
    private final BeanInstanceRegistry beanInstanceRegistry;

    @Getter
    private final BeanProxyDeciderResolver beanProxyDeciderResolver;

    @Getter
    private final CustomInjectorRegistry customInjectorRegistry;

    public DependencyManager() {
        this(new BeanDefinitionRegistry(), new BeanInstanceRegistry(), new BeanProxyDeciderResolver(), new CustomInjectorRegistry());
    }

    public DependencyManager(@NotNull BeanDefinitionRegistry beanDefinitionRegistry,
                             @NotNull BeanInstanceRegistry beanInstanceRegistry,
                             @NotNull BeanProxyDeciderResolver beanProxyDeciderResolver) {
        this(beanDefinitionRegistry, beanInstanceRegistry, beanProxyDeciderResolver, new CustomInjectorRegistry());
    }

    public DependencyManager(@NotNull BeanDefinitionRegistry beanDefinitionRegistry,
                             @NotNull BeanInstanceRegistry beanInstanceRegistry,
                             @NotNull BeanProxyDeciderResolver beanProxyDeciderResolver,
                             @NotNull CustomInjectorRegistry customInjectorRegistry) {
        this.beanDefinitionRegistry = Objects.requireNonNull(beanDefinitionRegistry, "beanDefinitionRegistry cannot be null.");
        this.beanInstanceRegistry = Objects.requireNonNull(beanInstanceRegistry, "beanInstanceRegistry cannot be null.");
        this.beanProxyDeciderResolver = Objects.requireNonNull(beanProxyDeciderResolver, "beanProxyDeciderResolver cannot be null.");
        this.customInjectorRegistry = Objects.requireNonNull(customInjectorRegistry, "customInjectorRegistry cannot be null.");
    }

    /**
     * Registers a custom injector.
     * <p>
     * Custom injectors allow modules to provide alternative ways of resolving dependencies.
     * They are consulted in order before falling back to the default bean resolution.
     *
     * @param injector the custom injector to register, not null
     */
    public void registerInjector(@NotNull CustomInjector injector) {
        Objects.requireNonNull(injector, "injector cannot be null");
        customInjectorRegistry.register(injector);
    }

    public <T> T resolveDependency(@NotNull Class<T> clazz, @Nullable String qualifier) {
        return resolveDependency((Type) clazz, qualifier);
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveDependency(@NotNull Type type, @Nullable String qualifier) {
        try {
            Objects.requireNonNull(type, "type cannot be null.");

            CollectionTypeUtils.CollectionTypeInfo collectionInfo = CollectionTypeUtils.extractCollectionTypeInfo(type);
            if (collectionInfo != null) {
                return (T) resolveCollectionDependency(collectionInfo.getCollectionClass(), collectionInfo.getElementType());
            }

            Class<T> clazz = CollectionTypeUtils.getRawClass(type);
            if (clazz == null) {
                return null;
            }

            List<BeanDefinition> definitions = beanDefinitionRegistry.getDefinitions(clazz);
            if (definitions.isEmpty()) {
                return null;
            }

            if (qualifier != null) {
                BeanDefinition definition = definitions.stream()
                        .filter(def -> qualifier.equals(def.getQualifierName()))
                        .findFirst()
                        .orElse(null);

                if (definition == null) {
                    return null;
                }

                return resolveFromDefinition(clazz, definition);
            }

            if (definitions.size() == 1) {
                return resolveFromDefinition(clazz, definitions.get(0));
            }

            List<BeanDefinition> primary = definitions.stream().filter(BeanDefinition::isPrimary).collect(Collectors.toList());

            if (primary.isEmpty()) {
                throw new IllegalStateException(String.format("No primary dependency found for class %s and qualifier %s", clazz.getSimpleName(), qualifier));
            }

            if (primary.size() > 1) {
                throw new IllegalStateException(String.format("Multiple primary dependencies found for class %s and qualifier %s", clazz.getSimpleName(), qualifier));
            }

            return resolveFromDefinition(clazz, primary.get(0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + type, e);
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

    /**
     * Resolves a dependency for the given injection point.
     * <p>
     * This method first consults all registered custom injectors in order. If any injector
     * handles the injection point, its result is returned. Otherwise, the default resolution
     * using the bean registry is performed.
     *
     * @param injectionPoint the injection point to resolve, not null
     * @return the resolved dependency, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T resolveDependency(@NotNull InjectionPoint injectionPoint) {
        Objects.requireNonNull(injectionPoint, "injectionPoint cannot be null");

        for (CustomInjector injector : customInjectorRegistry.getInjectors()) {
            if (injector.supports(injectionPoint)) {
                InjectionResult result = injector.resolve(injectionPoint);
                if (result.isHandled()) {
                    return (T) result.getValue();
                }
            }
        }

        return resolveDependency(injectionPoint.getType(), injectionPoint.getQualifier());
    }

    public <T> T registerDependency(@NotNull T instance, @Nullable String qualifier, boolean primary) {
        return registerDependency(instance, qualifier, primary, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull T instance,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    @Nullable DependencyReloadCallback reloadCallback) {
        Objects.requireNonNull(instance, "instance cannot be null.");

        Class<T> dependencyClass = (Class<T>) instance.getClass();

        for (Class<? super T> superInterface : ReflectionUtils.getSuperInterfaces(dependencyClass)) {
            registerDependency(superInterface, dependencyClass, instance, qualifier, primary, null, reloadCallback);
        }

        registerDependency(dependencyClass, dependencyClass, instance, qualifier, primary, null, reloadCallback);
        return instance;
    }

    public <T> T registerDependency(@NotNull Class<T> clazz, @NotNull T instance, @Nullable String qualifier, boolean primary) {
        return registerDependency(clazz, instance, qualifier, primary, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> clazz,
                                    @NotNull T instance,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    @Nullable DependencyReloadCallback reloadCallback) {
        Objects.requireNonNull(clazz, "clazz cannot be null.");
        Objects.requireNonNull(instance, "instance cannot be null.");

        registerDependency(clazz, (Class<? extends T>) instance.getClass(), instance, qualifier, primary, null, reloadCallback);
        return instance;
    }

    public <T> T registerDependency(@NotNull Class<T> dependencyClass,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    DependencyResolveResolver<T> resolver) {
        return registerDependency(dependencyClass, qualifier, primary, resolver, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<T> dependencyClass,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    DependencyResolveResolver<T> resolver,
                                    @Nullable DependencyReloadCallback reloadCallback) {
        Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");

        for (Class<? super T> superInterface : ReflectionUtils.getSuperInterfaces(dependencyClass)) {
            registerDependency((Class<T>) superInterface, dependencyClass, null, qualifier, primary, resolver, reloadCallback);
        }

        registerDependency(dependencyClass, dependencyClass, null, qualifier, primary, resolver, reloadCallback);
        return null;
    }

    public <T> T registerDependency(@NotNull Class<T> clazz,
                                    @NotNull Class<? extends T> dependencyClass,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    @Nullable DependencyResolveResolver<T> resolver) {
        return registerDependency(clazz, dependencyClass, qualifier, primary, resolver, null);
    }

    public <T> T registerDependency(@NotNull Class<T> clazz,
                                    @NotNull Class<? extends T> dependencyClass,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    @Nullable DependencyResolveResolver<T> resolver,
                                    @Nullable DependencyReloadCallback reloadCallback) {
        registerDependency(clazz, dependencyClass, null, qualifier, primary, resolver, reloadCallback);
        return null;
    }

    public <T> T registerDependency(@NotNull Class<T> clazz,
                                    @NotNull Class<? extends T> dependencyClass,
                                    @Nullable String qualifier,
                                    boolean primary) {
        return registerDependency(clazz, dependencyClass, qualifier, primary, null, null);
    }

    public <T> BeanDefinition registerDependency(@NotNull Class<T> clazz,
                                                 @NotNull Class<? extends T> dependencyClass,
                                                 @Nullable T instance,
                                                 @Nullable String qualifier,
                                                 boolean primary,
                                                 @Nullable DependencyResolveResolver<? extends T> resolver) {
        return registerDependency(clazz, dependencyClass, instance, qualifier, primary, resolver, null);
    }

    public <T> BeanDefinition registerDependency(@NotNull Class<T> clazz,
                                                 @NotNull Class<? extends T> dependencyClass,
                                                 @Nullable T instance,
                                                 @Nullable String qualifier,
                                                 boolean primary,
                                                 @Nullable DependencyResolveResolver<? extends T> resolver,
                                                 @Nullable DependencyReloadCallback reloadCallback) {
        try {
            Objects.requireNonNull(clazz, "clazz cannot be null.");
            Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");
            Preconditions.checkArgument(!(dependencyClass.isInterface() && resolver == null),
                    "You cannot register an interface without a resolver. Use DependencyResolveResolver to provide an implementation.");

            BeanUtils.detectCircularDependencies(dependencyClass, beanDefinitionRegistry.asMapView());

            BeanDefinition definition = new BeanDefinition(clazz, dependencyClass, qualifier, primary, resolver, reloadCallback);
            beanDefinitionRegistry.register(clazz, definition);

            if (instance != null) {
                beanInstanceRegistry.put(definition, instance);
            }

            return definition;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register dependency using (" + clazz + " -> " + dependencyClass + "): ", e);
        }
    }

    public void reloadDependencies() {
        beanInstanceRegistry.asMapView().entrySet().stream()
                .filter(entry -> entry.getKey().isReloadable())
                .filter(entry -> entry.getValue() != null)
                .distinct()
                .forEach(entry -> {
                    BeanDefinition definition = entry.getKey();
                    Object instance = entry.getValue();

                    try {
                        definition.getReloadCallback().reload(instance, this);
                        beanInstanceRegistry.put(definition, instance);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to reload dependency: " + definition.identifier(), e);
                    }
                });
    }

    public <T> @NotNull List<T> getInstancesByType(@NotNull Class<T> type) {
        return beanInstanceRegistry.getInstancesByType(type);
    }

    public <T> T resolveFromDefinition(@NotNull Class<T> requestedType, @NotNull BeanDefinition definition) throws Exception {
        Objects.requireNonNull(requestedType, "requestedType cannot be null.");
        Objects.requireNonNull(definition, "definition cannot be null.");

        if (beanInstanceRegistry.contains(definition)) {
            return requestedType.cast(beanInstanceRegistry.get(definition));
        }

        Object instance;
        if (definition.getResolver() != null) {
            @SuppressWarnings("unchecked")
            DependencyResolveResolver<T> resolver = (DependencyResolveResolver<T>) definition.getResolver();
            instance = resolver.resolve(requestedType);
        } else {
            instance = createInstance(definition);
        }

        if (instance == null) {
            return null;
        }

        beanInstanceRegistry.put(definition, instance);
        return requestedType.cast(instance);
    }

    public Object[] resolveArguments(@NotNull Parameter[] parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null.");

        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            InjectionPoint injectionPoint = InjectionPoint.fromParameter(param);
            args[i] = resolveDependency(injectionPoint);
        }

        return args;
    }

    public Object[] resolveArguments(@NotNull Executable executable) {
        Objects.requireNonNull(executable, "executable cannot be null.");
        return resolveArguments(executable.getParameters());
    }

    private Object createInstance(@NotNull BeanDefinition definition) throws Exception {
        Objects.requireNonNull(definition, "definition cannot be null.");

        Class<?> type = definition.getType();
        if (type == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Class<Object> rawType = (Class<Object>) type;

        Constructor<?> ctor = findInjectConstructor(type);
        if (ctor == null) {
            return null;
        }

        Object[] ctorArgs = resolveArguments(ctor);

        if (beanProxyDeciderResolver.shouldProxy(definition, this)) {
            if (Modifier.isFinal(type.getModifiers())) {
                throw new IllegalStateException("Cannot proxy final class: " + type.getName());
            }

            Object proxy = ComponentProxy.createProxy(
                    rawType,
                    null,
                    ctor.getParameterTypes(),
                    ctorArgs
            );

            injectDependencies(rawType, proxy);
            return proxy;
        }

        ctor.setAccessible(true);
        Object instance = ctor.newInstance(ctorArgs);
        injectDependencies(rawType, instance);
        return instance;
    }

    public <T> void injectDependencies(Class<T> clazz, @NotNull T instance) {
        Objects.requireNonNull(instance, "instance cannot be null.");

        try {
            setterInject(clazz, instance);
            fieldInject(clazz, instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to inject dependencies for: " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void injectDependencies(@NotNull T instance) {
        injectDependencies((Class<T>) instance.getClass(), instance);
    }

    public @Nullable Constructor<?> findInjectConstructor(@NotNull Class<?> type) {
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
            if (method.getParameterCount() != 1) {
                continue;
            }

            InjectionPoint injectionPoint = InjectionPoint.fromSetterMethod(method);
            boolean hasInjectAnnotation = method.isAnnotationPresent(Inject.class);
            boolean customInjectorSupports = customInjectorRegistry.customInjectorSupported(injectionPoint);

            if (hasInjectAnnotation || customInjectorSupports) {
                Object dep = resolveDependency(injectionPoint);

                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
    }

    private <T> void fieldInject(@NotNull Class<T> type, @NotNull T instance) throws IllegalAccessException {
        Objects.requireNonNull(type, "type cannot be null.");
        Objects.requireNonNull(instance, "instance cannot be null.");

        for (Field field : type.getDeclaredFields()) {
            InjectionPoint injectionPoint = InjectionPoint.fromField(field);
            boolean hasInjectAnnotation = field.isAnnotationPresent(Inject.class);
            boolean customInjectorSupports = customInjectorRegistry.customInjectorSupported(injectionPoint);

            if (hasInjectAnnotation || customInjectorSupports) {
                Object dep = resolveDependency(injectionPoint);

                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
    }

    public void clear() {
        beanDefinitionRegistry.clear();
        beanInstanceRegistry.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> resolveCollectionDependency(@NotNull Class<?> collectionClass, @NotNull Class<?> elementType) {
        List<BeanDefinition> definitions = beanDefinitionRegistry.getDefinitions(elementType);

        List<Object> instances = new ArrayList<>();
        for (BeanDefinition definition : definitions) {
            try {
                Object instance = resolveFromDefinition(elementType, definition);
                if (instance != null) {
                    instances.add(instance);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve bean for collection injection: " + definition.identifier(), e);
            }
        }

        Collection<Object> collection = createCollectionInstance(collectionClass);
        collection.addAll(instances);
        return (Collection<T>) collection;
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> createCollectionInstance(@NotNull Class<?> collectionClass) {
        if (List.class.isAssignableFrom(collectionClass)) {
            return new ArrayList<>();
        }
        if (Set.class.isAssignableFrom(collectionClass)) {
            return new HashSet<>();
        }
        if (Collection.class.isAssignableFrom(collectionClass)) {
            return new ArrayList<>();
        }

        try {
            return (Collection<Object>) collectionClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate collection type: " + collectionClass.getName(), e);
        }
    }
}
