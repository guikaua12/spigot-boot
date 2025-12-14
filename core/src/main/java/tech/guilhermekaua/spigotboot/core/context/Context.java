package tech.guilhermekaua.spigotboot.core.context;

import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

public interface Context {
    void initialize();

    boolean isInitialized();

    default <T> T getBean(Class<T> type) {
        return getBean(type, null);
    }

    default <T> T getBean(Class<T> type, String name) {
        return getDependencyManager().resolveDependency(type, name);
    }

    void registerBean(Object instance);

    void registerBean(Class<?> clazz);

    DependencyManager getDependencyManager();

    void scan(String basePackage);

    default void reload() {
        getDependencyManager().reloadDependencies();
    }

    void destroy();

    Plugin getPlugin();

    void registerShutdownHook(Runnable runnable);

    void unregisterShutdownHook(Runnable runnable);
}
