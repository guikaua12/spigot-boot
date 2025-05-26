package me.approximations.apxPlugin.core.utils;

import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class ReflectionUtils {
    private static final Set<Class<?>> pluginClasses = new HashSet<>();
    private static final Map<String, Set<Class<?>>> classes = new HashMap<>();

    public static Set<Class<?>> getClassesFromPackage(Class<?>... baseClasses) {
        return Arrays.stream(baseClasses)
                .flatMap((clazz) -> {
                    try {
                        String packageName = clazz.getPackage().getName();

                        if (classes.containsKey(packageName)) {
                            return classes.get(packageName).stream();
                        }

                        ClassPath classPath = null;

                        classPath = ClassPath.from(clazz.getClassLoader());


                        Set<Class<?>> loadedClasses = classPath.getAllClasses()
                                .stream()
                                .filter(classInfo -> classInfo.getPackageName().startsWith(packageName))
                                .map(ClassPath.ClassInfo::load)
                                .collect(Collectors.toSet());

                        classes.put(packageName, loadedClasses);

                        return loadedClasses.stream();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load classes from package: " + clazz.getPackage().getName(), e);
                    }
                }).collect(Collectors.toSet());
    }

    public static Set<Class<?>> getClassesAnnotatedWith(Class<?> baseClass, Class<? extends Annotation> annotationClass) {
        return getClassesFromPackage(baseClass)
                .stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotationClass))
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubClassesOf(Class<?> baseClass, Class<T> clazz) {
        return getClassesFromPackage(baseClass)
                .stream()
                .filter(clazz::isAssignableFrom)
                .filter(c -> !c.equals(clazz))
                .filter(c -> !c.isInterface())
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

    public static <T> Set<Class<? extends T>> getSubInterfacesOf(Class<?> baseClass, Class<T> clazz) {
        return getClassesFromPackage(baseClass)
                .stream()
                .filter(clazz::isAssignableFrom)
                .filter(Class::isInterface)
                .filter(c -> !c.equals(clazz))
                .map(c -> (Class<T>) c)
                .collect(Collectors.toSet());
    }

//    public static Set<Class<?>> getAllPluginClasses() {
//        if (!pluginClasses.isEmpty()) {
//            return pluginClasses;
//        }
//
//        pluginClasses.addAll(ApxPlugin.getClassPath().getAllClasses()
//                .stream()
//                .filter(clazz -> !clazz.getPackageName().contains("libs") && clazz.getPackageName().startsWith(ApxPlugin.getInstance().getClass().getPackage().getName()))
//                .filter(clazz -> !clazz.getName().equals("module-info"))
//                // skip ant and other build tool classes
//                .filter(clazz -> !clazz.getName().startsWith("org.apache.tools.ant"))
//                .map(classInfo -> Utils.sneakThrow(classInfo::load))
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet()));
//
//        return pluginClasses;
//    }

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
