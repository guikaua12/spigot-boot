package tech.guilhermekaua.spigotboot.core.context.dependency;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

@FunctionalInterface
public interface DependencyReloadCallback {
    void reload(@NotNull Object instance, @NotNull DependencyManager dependencyManager) throws Exception;
}
