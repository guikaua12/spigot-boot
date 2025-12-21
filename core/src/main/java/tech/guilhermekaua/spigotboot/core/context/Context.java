package tech.guilhermekaua.spigotboot.core.context;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.List;

public interface Context {
    void initialize();

    boolean isInitialized();

    default <T> @Nullable T getBean(@NotNull Class<T> type) {
        return getBean(type, null);
    }

    default <T> @Nullable T getBean(@NotNull Class<T> type, @Nullable String name) {
        return getDependencyManager().resolveDependency(type, name);
    }

    void registerBean(@NotNull Object instance);

    void registerBean(@NotNull Class<?> clazz);

    <T> @NotNull List<T> getBeansByType(@NotNull Class<T> type);

    @NotNull DependencyManager getDependencyManager();

    default void reload() {
        getDependencyManager().reloadDependencies();
    }

    void destroy();

    @NotNull List<Class<? extends Module>> getModulesToLoad();

    void setModulesToLoad(@NotNull List<Class<? extends Module>> modulesToLoad);

    Plugin getPlugin();

    void registerShutdownHook(@NotNull Runnable runnable);

    void unregisterShutdownHook(@NotNull Runnable runnable);
}
