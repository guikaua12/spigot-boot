package tech.guilhermekaua.spigotboot.core.context;

public interface Context {
    void initialize();

    boolean isInitialized();

    <T> T getBean(Class<T> type);

    <T> T getBean(Class<T> type, String name);

    void registerBean(Object instance);

    void registerBean(Class<?> clazz);

    DependencyManager getDependencyManager();

    void scan(String basePackage);

    default void reload() {
        getDependencyManager().reloadDependencies();
    }

    void destroy();
}
