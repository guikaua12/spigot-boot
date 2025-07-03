package tech.guilhermekaua.spigotboot.core.utils;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.Dependency;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.exceptions.CircularDependencyException;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class BeanUtils {
    public static String getQualifier(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Qualifier.class) ?
                element.getAnnotation(Qualifier.class).value() :
                null;
    }

    public static boolean getIsPrimary(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Primary.class);
    }

    public static DependencyReloadCallback createDependencyReloadCallback(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz);

        return (instance, dependencyManager) -> {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnReload.class)) {
                    continue;
                }

                try {
                    Object[] dependencies = Arrays.stream(method.getParameters())
                            .map(param -> dependencyManager.resolveDependency(param.getType(), getQualifier(param)))
                            .toArray(Object[]::new);

                    method.setAccessible(true);
                    method.invoke(instance, dependencies);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Detects circular dependencies in the dependency graph during registration.
     * This method analyzes constructor, field, and setter injection dependencies.
     *
     * @param newDependencyClass The class being registered
     * @param dependencyMap      The current dependency map
     * @throws CircularDependencyException if a circular dependency is detected
     */
    public static void detectCircularDependencies(@NotNull Class<?> newDependencyClass,
                                                  @NotNull Map<Class<?>, List<Dependency>> dependencyMap) {
        Objects.requireNonNull(newDependencyClass, "newDependencyClass cannot be null");
        Objects.requireNonNull(dependencyMap, "dependencyMap cannot be null");

        Set<Class<?>> visitedClasses = new HashSet<>();
        Set<Class<?>> currentPath = new LinkedHashSet<>();

        CircularDependencyResult result = hasCircularDependency(newDependencyClass, dependencyMap, visitedClasses, currentPath);
        if (result.hasCircularDependency) {
            throw new CircularDependencyException("Circular dependency detected: " +
                    String.join(" -> ", result.circularPath.stream()
                            .map(Class::getSimpleName)
                            .toArray(String[]::new)));
        }
    }

    /**
     * Helper class to return both the circular dependency status and the path
     */
    @RequiredArgsConstructor
    private static class CircularDependencyResult {
        final boolean hasCircularDependency;
        final List<Class<?>> circularPath;
    }

    /**
     * Recursively checks for circular dependencies using depth-first search.
     */
    private static CircularDependencyResult hasCircularDependency(@NotNull Class<?> currentClass,
                                                                  @NotNull Map<Class<?>, List<Dependency>> dependencyMap,
                                                                  @NotNull Set<Class<?>> visitedClasses,
                                                                  @NotNull Set<Class<?>> currentPath) {
        if (currentPath.contains(currentClass)) {
            List<Class<?>> cyclePath = new ArrayList<>();
            boolean foundCycleStart = false;
            for (Class<?> pathClass : currentPath) {
                if (pathClass.equals(currentClass)) {
                    foundCycleStart = true;
                }
                if (foundCycleStart) {
                    cyclePath.add(pathClass);
                }
            }
            cyclePath.add(currentClass);
            return new CircularDependencyResult(true, cyclePath);
        }

        if (visitedClasses.contains(currentClass)) {
            return new CircularDependencyResult(false, new ArrayList<>());
        }

        visitedClasses.add(currentClass);
        currentPath.add(currentClass);

        try {
            Set<Class<?>> dependencies = getAllDependenciesForClass(currentClass);

            for (Class<?> dependency : dependencies) {
                if (currentPath.contains(dependency)) {
                    List<Class<?>> cyclePath = new ArrayList<>();
                    boolean foundCycleStart = false;
                    for (Class<?> pathClass : currentPath) {
                        if (pathClass.equals(dependency)) {
                            foundCycleStart = true;
                        }
                        if (foundCycleStart) {
                            cyclePath.add(pathClass);
                        }
                    }
                    cyclePath.add(dependency);
                    return new CircularDependencyResult(true, cyclePath);
                }

                // if the dependency is already registered, check its dependencies recursively
                if (dependencyMap.containsKey(dependency)) {
                    List<Dependency> registeredDeps = dependencyMap.get(dependency);
                    if (registeredDeps != null) {
                        for (Dependency registeredDep : registeredDeps) {
                            CircularDependencyResult result = hasCircularDependency(registeredDep.getType(), dependencyMap, visitedClasses, currentPath);
                            if (result.hasCircularDependency) {
                                return result;
                            }
                        }
                    }
                }
            }

            return new CircularDependencyResult(false, new ArrayList<>());
        } finally {
            currentPath.remove(currentClass);
        }
    }

    /**
     * Extracts all dependency types from a class (constructor, field, and setter injection).
     */
    private static Set<Class<?>> getAllDependenciesForClass(@NotNull Class<?> clazz) {
        Set<Class<?>> dependencies = new HashSet<>();

        Constructor<?> injectConstructor = findInjectConstructor(clazz);
        if (injectConstructor != null) {
            dependencies.addAll(Arrays.asList(injectConstructor.getParameterTypes()));
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                dependencies.add(field.getType());
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                dependencies.add(method.getParameterTypes()[0]);
            }
        }

        return dependencies;
    }

    /**
     * Finds the constructor to be used for dependency injection.
     * Mirrors the logic from DependencyManager.findInjectConstructor().
     */
    private static Constructor<?> findInjectConstructor(@NotNull Class<?> type) {
        if (type.isInterface()) {
            return null;
        }

        List<Constructor<?>> annotatedCtors = Arrays.stream(type.getDeclaredConstructors())
                .filter(ctor -> ctor.isAnnotationPresent(Inject.class))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        if (annotatedCtors.size() > 1) {
            return null;
        }

        if (!annotatedCtors.isEmpty()) {
            return annotatedCtors.get(0);
        }

        Constructor<?>[] ctors = type.getDeclaredConstructors();
        if (ctors.length == 1) {
            return ctors[0];
        }

        return null;
    }
}
