package me.approximations.apxPlugin.di.manager;

import me.approximations.apxPlugin.di.DIContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DependencyManager {
    private final DIContainer diContainer = new DIContainer();

    public <T> T resolveDependency(@NotNull Class<T> clazz) {
        Objects.requireNonNull(clazz, "class cannot be null.");

        return diContainer.resolve(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T registerDependency(@NotNull T dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        return (T) registerDependency(dependency.getClass(), dependency);
    }

    public <T> T registerDependency(@NotNull Class<? extends T> clazz, @NotNull T dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null.");

        diContainer.register(clazz, dependency);

        return dependency;
    }

    public <T> void registerDependency(@NotNull Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz cannot be null.");

        if (clazz.isInterface()) {
            throw new IllegalArgumentException("Cannot register an interface as a dependency.");
        }

        diContainer.register(clazz, clazz);
        if (clazz.getSuperclass() != null && clazz.getSuperclass().isInterface()) {
            registerDependency(clazz.getSuperclass(), clazz);
        }
    }

    public <T> void registerDependency(@NotNull Class<T> clazz, @NotNull Class<? extends T> dependencyClass) {
        Objects.requireNonNull(clazz, "clazz cannot be null.");
        Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");

        diContainer.register(clazz, dependencyClass);
    }

}