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
package tech.guilhermekaua.spigotboot.core.utils;

import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.scanner.ClassPathScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReflectionUtils {

    public static Set<Class<?>> getClassesAnnotatedWith(String basePackage, Class<? extends Annotation> annotationClass) {
        ClassPathScanner scanner = new ClassPathScanner(ReflectionUtils.class.getClassLoader(), basePackage);
        return scanner.getTypesAnnotatedWith(annotationClass);
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(@Nullable String basePackage, Class<T> clazz, boolean ignoreInterfaces) {
        String[] packages = basePackage != null ? new String[]{basePackage} : new String[0];
        ClassPathScanner scanner = new ClassPathScanner(ReflectionUtils.class.getClassLoader(), packages);

        Set<Class<? extends T>> subTypes = scanner.getSubTypesOf(clazz);
        if (ignoreInterfaces) {
            return subTypes.stream()
                    .filter(c -> !c.isInterface())
                    .collect(Collectors.toSet());
        }
        return subTypes;
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(String basePackage, Class<T> clazz) {
        return getSubClassesOf(basePackage, clazz, false);
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<T> clazz, boolean ignoreInterfaces) {
        return getSubClassesOf(null, clazz, ignoreInterfaces);
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<T> clazz) {
        return getSubClassesOf(null, clazz);
    }

    public static Set<Field> getFieldsAnnotatedWith(Class<? extends Annotation> annotationClass, Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    public static Set<Method> getMethodsAnnotatedWith(Class<? extends Annotation> annotationClass, Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    public static <T> List<Class<? super T>> getSuperInterfaces(Class<?> type) {
        Objects.requireNonNull(type, "type cannot be null.");

        return Arrays.stream(type.getInterfaces())
                .map(clazz -> (Class<? super T>) clazz)
                .collect(Collectors.toList());

    }
}
