package tech.guilhermekaua.spigotboot.core.context.registration;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

@RequiredArgsConstructor
public class DefaultBeanRegistrar implements BeanRegistrar {
    private final DependencyManager dependencyManager;
    private final PhaseChecker phaseChecker;

    @Override
    public <T> @NotNull T registerInstance(@NotNull T instance,
                                           @Nullable String qualifier,
                                           boolean primary) {
        return registerInstance(instance, qualifier, primary, null);
    }

    @Override
    public <T> @NotNull T registerInstance(@NotNull T instance,
                                           @Nullable String qualifier,
                                           boolean primary,
                                           @Nullable DependencyReloadCallback reloadCallback) {
        phaseChecker.checkCanRegister();
        return dependencyManager.registerDependency(instance, qualifier, primary, reloadCallback);
    }

    @Override
    public <T> @NotNull T registerInstance(@NotNull Class<T> requestedType,
                                           @NotNull T instance,
                                           @Nullable String qualifier,
                                           boolean primary) {
        phaseChecker.checkCanRegister();
        return dependencyManager.registerDependency(requestedType, instance, qualifier, primary);
    }

    @Override
    public <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> clazz,
                                                          @Nullable String qualifier,
                                                          boolean primary) {
        return registerDefinition(clazz, qualifier, primary, null);
    }

    @Override
    public <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> clazz,
                                                          @Nullable String qualifier,
                                                          boolean primary,
                                                          @Nullable DependencyReloadCallback reloadCallback) {
        phaseChecker.checkCanRegister();
        return dependencyManager.registerDependency(
                clazz,
                clazz,
                null,
                qualifier,
                primary,
                null,
                reloadCallback
        );
    }

    @Override
    public <T, U extends T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> requestedType,
                                                                       @NotNull Class<U> implementationType,
                                                                       @Nullable String qualifier,
                                                                       boolean primary,
                                                                       @Nullable DependencyResolveResolver<U> resolver) {
        return registerDefinition(requestedType, implementationType, qualifier, primary, resolver, null);
    }

    @Override
    public <T, U extends T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> requestedType,
                                                                       @NotNull Class<U> implementationType,
                                                                       @Nullable String qualifier,
                                                                       boolean primary,
                                                                       @Nullable DependencyResolveResolver<U> resolver,
                                                                       @Nullable DependencyReloadCallback reloadCallback) {
        phaseChecker.checkCanRegister();
        return dependencyManager.registerDependency(
                requestedType,
                implementationType,
                null,
                qualifier,
                primary,
                resolver,
                reloadCallback
        );
    }

    @FunctionalInterface
    public interface PhaseChecker {
        void checkCanRegister();
    }
}


