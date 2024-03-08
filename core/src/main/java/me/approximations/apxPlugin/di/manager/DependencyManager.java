package me.approximations.apxPlugin.di.manager;

import lombok.Getter;
import me.approximations.apxPlugin.di.annotations.DependencyRegister;
import me.approximations.apxPlugin.di.annotations.Inject;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DependencyManager {
    @Getter
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final Reflections reflections;

    public DependencyManager(@NotNull Reflections reflections) {
        this.reflections = reflections;
    }

    @SuppressWarnings("unchecked")
    public <T> T getDependency(@NotNull Class<T> clazz) {
        Objects.requireNonNull(clazz, "class cannot be null.");

        return (T) dependencies.get(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull T dependency) {
        return (T) registerDependency((Class<? super T>) dependency.getClass(), dependency);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull Class<? extends T> clazz, @NotNull T dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        dependencies.put(clazz, dependency);

        return dependency;
    }

    public void registerDependencies() {
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
        final Stream<Field> classStream = Arrays.stream(object.getClass().getDeclaredFields());
        final Stream<Field> superClassStream = Arrays.stream(object.getClass().getSuperclass().getDeclaredFields());

        return Stream.concat(classStream, superClassStream).filter(field -> field.isAnnotationPresent(Inject.class)).collect(Collectors.toSet());
    }
}
