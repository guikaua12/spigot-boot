package me.approximations.spigotboot.core.utils;

import com.google.common.base.Strings;
import com.google.common.reflect.ClassPath;
import org.jetbrains.annotations.Nullable;

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

    public static Set<Class<?>> getClassesAnnotatedWith(Class<?> baseClass, Class<? extends Annotation> annotationClass) {
        return getClassesFromPackage(baseClass)
                .stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(@Nullable Class<?> baseClass, Class<T> clazz, boolean ignoreInterfaces) {
        return getClassesFromPackage(baseClass != null ? baseClass.getPackage().getName() : "")
                .stream()
                .filter(clazz::isAssignableFrom)
                .filter(c -> !c.equals(clazz))
                .filter(c -> !ignoreInterfaces || !c.isInterface())
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(@Nullable Class<?> baseClass, Class<T> clazz) {
        return getSubClassesOf(baseClass, clazz, false);
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
}
