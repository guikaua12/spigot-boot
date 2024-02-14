package me.approximations.apxPlugin.dependencyInjection.manager;

import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.dependencyInjection.annotations.DependencyRegister;
import me.approximations.apxPlugin.dependencyInjection.annotations.Inject;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;


public class DependencyManager {
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final Reflections reflections;

    public DependencyManager(@NotNull ApxPlugin plugin) {
        this.reflections = new Reflections(plugin.getClass().getPackage().getName(), new SubTypesScanner(), new TypeAnnotationsScanner());

        registerDependencies();
        injectDependencies();
    }

    @SuppressWarnings("unchecked")
    public <T> T getDependency(@NotNull Class<T> clazz) {
        Objects.requireNonNull(clazz, "class cannot be null.");

        return (T) dependencies.get(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Object dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        dependencies.put(dependency.getClass(), dependency);

        return (T) dependency;
    }

    public void registerDependencies() {
        this.dependencies.clear();

        final Set<Class<?>> dependencyRegisters = reflections.getTypesAnnotatedWith(DependencyRegister.class);

        for (Class<?> dependencyRegister : dependencyRegisters) {
            try {
                final Object instance = dependencyRegister.newInstance();

                for (final Method declaredMethod : dependencyRegister.getDeclaredMethods()) {
                    final Object dependency = declaredMethod.invoke(instance);
                    this.registerDependency(dependency);
                }

            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void injectDependencies() {
        for (Object dependency : dependencies.values()) {
            injectDependencies(dependency);
        }
    }

    public void injectDependencies(@NotNull Object object) {
        Objects.requireNonNull(object, "object cannot be null.");

        for (final Field field : getInjectableFields(object)) {
            final Class<?> fieldType = field.getType();
            final Object dependency = this.getDependency(fieldType);

            if (dependency == null) {
                throw new RuntimeException("Dependency not found for field " + field.getName() + " in class " + field.getDeclaringClass().getName());
            }

            try {
                field.setAccessible(true);
                field.set(object, dependency);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                field.setAccessible(false);
            }
        }
    }

    private Set<Field> getInjectableFields(@NotNull Object object) {
        Objects.requireNonNull(object, "object cannot be null.");

        return Arrays.stream(object.getClass().getDeclaredFields()).filter(field -> field.isAnnotationPresent(Inject.class)).collect(Collectors.toSet());
    }
}
