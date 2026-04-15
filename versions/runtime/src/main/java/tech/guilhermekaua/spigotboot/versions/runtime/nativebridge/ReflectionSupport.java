/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.nativebridge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Shared reflection helpers used by version-local adapters.
 *
 * @since 2.0.2
 */
public final class ReflectionSupport {
    private static final Unsafe UNSAFE = resolveUnsafe();

    private ReflectionSupport() {
    }

    public static @NotNull Class<?> requireClass(@NotNull String... candidateNames) {
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");
        List<ClassLoader> classLoaders = candidateClassLoaders();
        for (String candidateName : candidateNames) {
            for (ClassLoader classLoader : classLoaders) {
                try {
                    return Class.forName(candidateName, false, classLoader);
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        throw new IllegalStateException(
                "Could not resolve any of the requested classes: " + Arrays.toString(candidateNames) + "."
        );
    }

    public static @NotNull Method requireNamedMethod(
            @NotNull Class<?> type,
            @NotNull String[] candidateNames,
            @NotNull Class<?>... parameterTypes
    ) {
        Method method = findNamedMethod(type, candidateNames, parameterTypes);
        if (method == null) {
            throw new IllegalStateException(
                    "Could not resolve a method on " + type.getName() + "."
            );
        }
        return method;
    }

    public static @Nullable Method findNamedMethod(
            @NotNull Class<?> type,
            @NotNull String[] candidateNames,
            @NotNull Class<?>... parameterTypes
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes cannot be null");

        Class<?> current = type;
        while (current != null) {
            for (String candidateName : candidateNames) {
                try {
                    Method method = current.getDeclaredMethod(candidateName, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static @Nullable Method findCompatibleMethod(
            @NotNull Class<?> type,
            @NotNull String[] candidateNames,
            @NotNull Class<?>... argumentTypes
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");
        Objects.requireNonNull(argumentTypes, "argumentTypes cannot be null");

        for (String candidateName : candidateNames) {
            Method method = findCompatibleMethodByName(type, candidateName, argumentTypes);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static @Nullable Method findCompatibleMethodByName(
            @NotNull Class<?> type,
            @NotNull String candidateName,
            @NotNull Class<?>... argumentTypes
    ) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!candidateName.equals(method.getName())) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != argumentTypes.length) {
                    continue;
                }

                boolean compatible = true;
                for (int index = 0; index < parameterTypes.length; index++) {
                    if (!parameterTypes[index].isAssignableFrom(argumentTypes[index])) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) {
                    continue;
                }

                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static @NotNull Method requireCompatibleMethod(
            @NotNull Class<?> type,
            @NotNull String[] candidateNames,
            @NotNull Class<?>... argumentTypes
    ) {
        Method method = findCompatibleMethod(type, candidateNames, argumentTypes);
        if (method == null) {
            throw new IllegalStateException(
                    "Could not resolve a compatible method on " + type.getName() + "."
            );
        }
        return method;
    }

    public static @NotNull Method requireMethodBySignature(
            @NotNull Class<?> type,
            @NotNull Class<?> returnType,
            @NotNull Class<?>... parameterTypes
    ) {
        Method method = findMethodBySignature(type, returnType, parameterTypes);
        if (method == null) {
            throw new IllegalStateException(
                    "Could not resolve a method by signature on " + type.getName() + "."
            );
        }
        return method;
    }

    public static @Nullable Method findMethodBySignature(
            @NotNull Class<?> type,
            @NotNull Class<?> returnType,
            @NotNull Class<?>... parameterTypes
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(returnType, "returnType cannot be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes cannot be null");

        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!returnType.equals(method.getReturnType())) {
                    continue;
                }
                Class<?>[] methodParameters = method.getParameterTypes();
                if (methodParameters.length != parameterTypes.length) {
                    continue;
                }
                boolean compatible = true;
                for (int index = 0; index < parameterTypes.length; index++) {
                    if (!methodParameters[index].equals(parameterTypes[index])) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static @NotNull Collection<Method> collectNamedMethods(
            @NotNull Class<?> type,
            @NotNull String[] candidateNames,
            @NotNull Class<?>... parameterTypes
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes cannot be null");

        List<Method> methods = new ArrayList<Method>();
        for (String candidateName : candidateNames) {
            Method method = findNamedMethod(type, new String[]{candidateName}, parameterTypes);
            if (method != null) {
                methods.add(method);
            }
        }
        return methods;
    }

    public static @NotNull Constructor<?> requireCompatibleConstructor(
            @NotNull Class<?> type,
            @NotNull Class<?>... argumentTypes
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(argumentTypes, "argumentTypes cannot be null");

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != argumentTypes.length) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!parameterTypes[index].isAssignableFrom(argumentTypes[index])) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }

            constructor.setAccessible(true);
            return constructor;
        }

        throw new IllegalStateException(
                "Could not resolve a compatible constructor on " + type.getName() + "."
        );
    }

    public static @Nullable Field findField(@NotNull Class<?> type, @NotNull String... candidateNames) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");

        Class<?> current = type;
        while (current != null) {
            for (String candidateName : candidateNames) {
                try {
                    Field field = current.getDeclaredField(candidateName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static @NotNull Field requireField(@NotNull Class<?> type, @NotNull String... candidateNames) {
        Field field = findField(type, candidateNames);
        if (field == null) {
            throw new IllegalStateException(
                    "Could not resolve a field on " + type.getName() + "."
            );
        }
        return field;
    }

    public static @Nullable Object readField(@NotNull Field field, @Nullable Object target) {
        Objects.requireNonNull(field, "field cannot be null");
        try {
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to read field '" + field.getName() + "'.",
                    exception
            );
        }
    }

    public static void writeField(@NotNull Field field, @Nullable Object target, @Nullable Object value) {
        Objects.requireNonNull(field, "field cannot be null");
        try {
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to write field '" + field.getName() + "'.",
                    exception
            );
        }
    }

    public static @NotNull Object invoke(
            @NotNull Method method,
            @Nullable Object target,
            @Nullable Object... arguments
    ) {
        Objects.requireNonNull(method, "method cannot be null");
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to invoke method '" + method.getName() + "'.",
                    exception
            );
        }
    }

    public static @NotNull Object instantiate(
            @NotNull Constructor<?> constructor,
            @Nullable Object... arguments
    ) {
        Objects.requireNonNull(constructor, "constructor cannot be null");
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to instantiate '" + constructor.getDeclaringClass().getName() + "'.",
                    exception
            );
        }
    }

    public static @NotNull Object allocateInstance(@NotNull Class<?> type) {
        Objects.requireNonNull(type, "type cannot be null");
        try {
            return UNSAFE.allocateInstance(type);
        } catch (InstantiationException exception) {
            throw new IllegalStateException(
                    "Could not allocate an instance of '" + type.getName() + "' without invoking constructors.",
                    exception
            );
        }
    }

    private static @NotNull List<ClassLoader> candidateClassLoaders() {
        List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();

        addIfPresent(classLoaders, Thread.currentThread().getContextClassLoader());
        addIfPresent(classLoaders, ReflectionSupport.class.getClassLoader());
        addIfPresent(classLoaders, ClassLoader.getSystemClassLoader());

        if (classLoaders.isEmpty()) {
            classLoaders.add(ClassLoader.getPlatformClassLoader());
        }
        return classLoaders;
    }

    private static void addIfPresent(@NotNull List<ClassLoader> classLoaders, @Nullable ClassLoader classLoader) {
        if (classLoader != null && !classLoaders.contains(classLoader)) {
            classLoaders.add(classLoader);
        }
    }

    private static @NotNull Unsafe resolveUnsafe() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve sun.misc.Unsafe.", exception);
        }
    }
}
