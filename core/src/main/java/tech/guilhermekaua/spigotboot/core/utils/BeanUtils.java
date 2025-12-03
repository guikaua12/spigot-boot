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

/**
 * Utility class providing helper methods for bean management in the Spigot Boot dependency injection system.
 * <p>
 * This class handles qualifier extraction, primary bean detection, reload callback creation, and circular dependency
 * detection during bean registration. It supports constructor, field, and setter injection analysis.
 */
public final class BeanUtils {
    /**
     * Retrieves the qualifier value from the {@link Qualifier} annotation on the given annotated element, if present.
     * <p>
     * The qualifier is used to disambiguate beans of the same type during dependency resolution.
     *
     * @param element the annotated element (e.g., field, parameter, or method) to inspect, not null
     * @return the qualifier string value, or {@code null} if no {@link Qualifier} annotation is present
     */
    public static String getQualifier(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Qualifier.class) ?
                element.getAnnotation(Qualifier.class).value() :
                null;
    }

    /**
     * Determines if the given annotated element is marked as the primary bean using the {@link Primary} annotation.
     * <p>
     * Primary beans are preferred during autowiring when multiple candidates exist for the same type.
     *
     * @param element the annotated element (e.g., class or bean definition) to check, not null
     * @return {@code true} if the element has the {@link Primary} annotation, {@code false} otherwise
     */
    public static boolean getIsPrimary(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Primary.class);
    }

    /**
     * Creates a reload callback for the specified class that automatically invokes all methods annotated with
     * {@link OnReload} after dependency reinjection.
     * <p>
     * The callback resolves dependencies for method parameters using the provided dependency manager and handles
     * any exceptions by wrapping them in a {@link RuntimeException}. This enables beans to react to configuration
     * reloads or dependency updates.
     *
     * @param clazz the class for which to generate the reload callback, not null
     * @return a {@link DependencyReloadCallback} that performs the reload logic for the class
     */
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
     * Detects circular dependencies in the dependency graph during bean registration.
     * <p>
     * This method performs a depth-first search to analyze dependencies from constructors, fields, and setter methods
     * annotated with {@link Inject}. It checks both the new class being registered and existing dependencies in the map.
     * If a cycle is found, a {@link CircularDependencyException} is thrown with the cycle path for debugging.
     *
     * @param newDependencyClass the class being registered as a new dependency, not null
     * @param dependencyMap      the current map of registered dependencies mapping types to their instances, not null
     * @throws CircularDependencyException if a circular dependency path is detected in the graph
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
     * Recursively checks for circular dependencies using depth-first search on the dependency graph.
     * <p>
     * This helper method tracks the current recursion path and visited classes to identify cycles. It delegates to
     * registered dependencies if they exist in the map.
     *
     * @param currentClass   the class currently under inspection, not null
     * @param dependencyMap  the map of registered dependencies, not null
     * @param visitedClasses the set of classes already fully processed, not null
     * @param currentPath    the current recursion stack to detect cycles, not null
     * @return a result containing whether a circular dependency was found and the cycle path if applicable
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
     * Helper record to encapsulate the result of a circular dependency check, including the status and the detected path.
     */
    @RequiredArgsConstructor
    private static class CircularDependencyResult {
        final boolean hasCircularDependency;
        final List<Class<?>> circularPath;
    }

    /**
     * Extracts all unique dependency types required by the given class through constructor parameters, injected fields,
     * and setter methods.
     * <p>
     * Constructor injection is prioritized based on {@link Inject} annotations or single-constructor heuristics.
     * Only single-parameter setter methods with {@link Inject} are considered.
     *
     * @param clazz the class to analyze for dependencies, not null
     * @return a set of all required dependency types, possibly empty
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
     * Identifies the constructor suitable for dependency injection.
     * <p>
     * Preferences: explicitly annotated constructors (at most one), then single no-arg or parameterized constructors.
     * Interfaces have no injectable constructors.
     *
     * @param type the class type to find a constructor for, not null
     * @return the selected inject constructor, or {@code null} if multiple annotated constructors or no suitable one exists
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
