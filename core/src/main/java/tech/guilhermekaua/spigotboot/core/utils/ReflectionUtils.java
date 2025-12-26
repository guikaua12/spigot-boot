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

import com.google.common.base.Strings;
import com.google.common.reflect.ClassPath;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class ReflectionUtils {
    private static final Set<Class<?>> pluginClasses = new HashSet<>();
    private static final Map<String, Set<Class<?>>> classes = new HashMap<>();

    public static Set<Class<?>> getClassesFromPackage(String... packages) {
        return Arrays.stream(packages)
                .map(packageName -> Strings.nullToEmpty(packageName).trim())
                .flatMap((packageName) -> {
                    try {
                        if (classes.containsKey(packageName)) {
                            return classes.get(packageName).stream();
                        }

                        ClassPath classPath = ClassPath.from(ReflectionUtils.class.getClassLoader());

                        Set<Class<?>> loadedClasses = classPath.getAllClasses()
                                .stream()
                                .filter(classInfo -> classInfo.getPackageName().startsWith(packageName))
                                .filter(cls -> !cls.getName().equals("module-info"))
                                // skip ant and other build tool classes
                                .filter(cls -> !cls.getName().startsWith("org.apache.tools.ant"))
                                .map((cls) -> Utils.sneakThrow(cls::load))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());

                        classes.put(packageName, loadedClasses);

                        return loadedClasses.stream();
                    } catch (Throwable e) {
                        throw new RuntimeException("Failed to load classes from package: " + packageName, e);
                    }
                }).collect(Collectors.toSet());
    }

    public static Set<Class<?>> getClassesFromPackage(Class<?>... classes) {
        return getClassesFromPackage(
                Arrays.stream(classes)
                        .map(Class::getPackage)
                        .map(Package::getName)
                        .toArray(String[]::new)
        );
    }

    public static Set<Class<?>> getClassesAnnotatedWith(String basePackage, Class<? extends Annotation> annotationClass) {
        Reflections reflections = new Reflections(basePackage, new SubTypesScanner(), new TypeAnnotationsScanner());

        return reflections.getTypesAnnotatedWith(annotationClass);
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(@Nullable String basePackage, Class<T> clazz, boolean ignoreInterfaces) {
        Reflections reflections = new Reflections(basePackage, new SubTypesScanner(), new TypeAnnotationsScanner());

        return reflections.getSubTypesOf(clazz);
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
