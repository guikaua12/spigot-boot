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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class DependencyManager {
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final Reflections reflections;

    public DependencyManager(@NotNull ApxPlugin plugin) {
        this.reflections = new Reflections(plugin.getClass().getPackage().getName(), new SubTypesScanner(), new TypeAnnotationsScanner());

        registerDependencies();
        injectDependencies();
    }

    @SuppressWarnings("unchecked")
    public <T> T getDependency(Class<T> clazz) {
        return (T) dependencies.get(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(Object dependency) {
        dependencies.put(dependency.getClass(), dependency);

        return (T) dependency;
    }

    private void registerDependencies() {
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

    private void injectDependencies() {
        for (Object dependency : dependencies.values()) {
            for (Field field : dependency.getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) continue;

                final Class<?> fieldType = field.getType();
                final Object fieldDependency = dependencies.get(fieldType);

                if (fieldDependency == null) {
                    throw new RuntimeException("Dependency not found for field " + field.getName() + " in class " + dependency.getClass().getName());
                }

                try {
                    field.setAccessible(true);
                    field.set(dependency, fieldDependency);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } finally {
                    field.setAccessible(false);
                }
            }
        }
    }
}
