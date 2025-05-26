package me.approximations.apxPlugin.utils;

import lombok.Getter;
import me.approximations.apxPlugin.ApxPlugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReflectionUtils {
    @Getter(lazy = true)
    private static final Set<Class<?>> pluginClasses = getAllPluginClasses();

    public static Set<Class<?>> getClassesAnnotatedWith(Class<? extends Annotation> annotationClass) {
        return pluginClasses
                .stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<T> clazz) {
        return pluginClasses
                .stream()
                .filter(clazz::isAssignableFrom)
                .filter(c -> !c.equals(clazz))
                .filter(c -> !c.isInterface())
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubInterfacesOf(Class<T> clazz) {
        return pluginClasses
                .stream()
                .filter(clazz::isAssignableFrom)
                .filter(Class::isInterface)
                .filter(c -> !c.equals(clazz))
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

    private static Set<Class<?>> getAllPluginClasses() {
        return ApxPlugin.getClassPath().getAllClasses()
                .stream()
                .filter(clazz -> !clazz.getPackageName().contains("libs") && clazz.getPackageName().startsWith(ApxPlugin.getInstance().getClass().getPackage().getName()))
                .filter(clazz -> !clazz.getName().equals("module-info"))
                // skip ant and other build tool classes
                .filter(clazz -> !clazz.getName().startsWith("org.apache.tools.ant"))
                .map(classInfo -> Utils.sneakThrow(classInfo::load))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Set<Field> getFieldsAnnotatedWith(Class<? extends Annotation> annotationClass, Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> clazz.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }
}
