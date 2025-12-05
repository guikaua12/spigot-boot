package tech.guilhermekaua.spigotboot.core.context.dependency;

@FunctionalInterface
public interface DependencyResolveResolver<T> {
    T resolve(Class<T> type);
}
