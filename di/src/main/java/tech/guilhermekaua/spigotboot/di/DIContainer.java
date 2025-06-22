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
package tech.guilhermekaua.spigotboot.di;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.di.exceptions.CircularDependencyException;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

@Getter
public class DIContainer {
    private final Map<Class<?>, ClassMetadata<?>> typeMappings = new HashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();
    private final LinkedList<Class<?>> resolutionPath = new LinkedList<>();

    @SuppressWarnings("unchecked")
    public <T> ClassMetadata<T> register(Class<T> baseType, Class<? extends T> implType) {
        ClassMetadata<T> classMetadata = new ClassMetadata<>((Class<T>) implType, getInjectConstructor((Class<T>) implType));
        typeMappings.put(baseType, classMetadata);

        return classMetadata;
    }

    @SuppressWarnings("unchecked")
    public <T> ClassMetadata<T> register(Class<? extends T> baseType, T dependency) {
        ClassMetadata<T> classMetadata = new ClassMetadata<>(ProxyUtils.getRealClass(dependency), getInjectConstructor(ProxyUtils.getRealClass(dependency)));
        typeMappings.put(baseType, classMetadata);
        singletons.put(baseType, dependency);

        return classMetadata;
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        if (!typeMappings.containsKey(type)) {
            return null;
        }

        int existingIndex = resolutionPath.indexOf(type);
        if (existingIndex >= 0) {
            throw new CircularDependencyException(type, resolutionPath, existingIndex);
        }

        resolutionPath.addLast(type);

        try {
            if (singletons.containsKey(type)) {
                return type.cast(singletons.get(type));
            }

            ClassMetadata<?> implMetadata = typeMappings.get(type);

            T instance = (T) createInstance(implMetadata);
            singletons.put(type, instance);
            return instance;
        } catch (CircularDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + type, e);
        } finally {
            resolutionPath.removeLast();
        }
    }

    private <T> T createInstance(ClassMetadata<T> metadata) throws Exception {
        Constructor<T> constructor = metadata.getInjectConstructor();

        T instance = instantiateWithConstructor(metadata.getClazz(), constructor);

        injectDependencies(instance);

        return instance;
    }

    public <T> void injectDependencies(T instance) {
        Objects.requireNonNull(instance, "instance cannot be null.");
        Class<T> type = ProxyUtils.getRealClass(instance);

        try {
            setterInject(type, instance);
            fieldInject(type, instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to inject dependencies for: " + type.getName(), e);
        }
    }

    private <T> void setterInject(Class<T> type, T instance) throws IllegalAccessException, InvocationTargetException {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                Class<?> depType = method.getParameterTypes()[0];
                Object dep = resolve(depType);
                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
    }

    private <T> void fieldInject(Class<T> type, T instance) throws IllegalAccessException {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object dep = resolve(field.getType());
                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
    }

    private <T> T instantiateWithConstructor(Class<T> type, Constructor<?> ctor) throws Exception {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolve(paramTypes[i]);
        }
        ctor.setAccessible(true);
        return type.cast(ctor.newInstance(params));
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable Constructor<T> getInjectConstructor(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null.");

        if (type.isInterface()) {
            return null;
        }

        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.isAnnotationPresent(Inject.class)) {
                return (Constructor<T>) ctor;
            }
        }

        Constructor<T>[] ctors = (Constructor<T>[]) type.getDeclaredConstructors();
        Constructor<T> selectedCtor = null;
        int maxParams = -1;
        for (Constructor<T> ctor : ctors) {
            int paramCount = ctor.getParameterCount();
            if (paramCount > maxParams) {
                maxParams = paramCount;
                selectedCtor = ctor;
            }
        }

        return selectedCtor;
    }
}

