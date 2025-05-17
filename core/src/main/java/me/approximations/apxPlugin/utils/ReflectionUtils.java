package me.approximations.apxPlugin.utils;

import com.google.common.reflect.ClassPath;
import me.approximations.apxPlugin.ApxPlugin;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReflectionUtils {
    private static Set<Class<?>> pluginClasses;

    public static Set<Class<?>> getClassesAnnotatedWith(Class<? extends Annotation> annotationClass) {
        return getAllPluginClasses()
                .stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<T> clazz) {
        return getAllPluginClasses()
                .stream()
                .filter(clazz::isAssignableFrom)
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

    public static Set<Class<?>> getAllPluginClasses() {
        if (pluginClasses == null) {
            try {
                pluginClasses = ApxPlugin.getClassPath().getAllClasses()
                        .stream()
                        .filter(clazz -> !clazz.getPackageName().contains("libs") && clazz.getPackageName().startsWith(ApxPlugin.getInstance().getClass().getPackage().getName()))
                        // skip module-info
                        .filter(clazz -> !clazz.getName().equals("module-info"))
                        // skip ant and other build tool classes
                        .filter(clazz -> !clazz.getName().startsWith("org.apache.tools.ant"))
                        .map(classInfo -> Utils.sneakThrow(classInfo::load))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            } catch (Exception ignored) {
            }
        }

        return pluginClasses;
    }
}
