package me.approximations.apxPlugin.utils;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import me.approximations.apxPlugin.ApxPlugin;

import java.lang.annotation.Annotation;
import java.util.Set;

public final class ReflectionUtils {
    private static Set<Class<?>> pluginClasses;

    public static Set<Class<?>> getClassesAnnotatedWith(Class<? extends Annotation> annotationClass) {
        return pluginClasses
                .stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotationClass))
                .collect(ImmutableSet.toImmutableSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<T> clazz) {
        return pluginClasses
                .stream()
                .filter(clazz::isAssignableFrom)
                .map(c -> (Class<T>) c)
                .collect(ImmutableSet.toImmutableSet());
    }

    public static Set<Class<?>> getAllPluginClasses() {
        if (pluginClasses == null) {
            pluginClasses = ApxPlugin.getClassPath().getAllClasses()
                    .stream()
                    .filter(clazz -> clazz.getPackageName().startsWith(ApxPlugin.getInstance().getClass().getPackage().getName()))
                    .map(ClassPath.ClassInfo::load)
                    .collect(ImmutableSet.toImmutableSet());
        }

        return pluginClasses;
    }
}
