package tech.guilhermekaua.spigotboot.core.context.registration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;

public interface BeanRegistrar {
    /**
     * Register a bean instance.
     *
     * @param instance  the instance to register
     * @param qualifier optional qualifier name
     * @param primary   whether this is a primary bean
     * @return the registered instance
     */
    <T> @NotNull T registerInstance(@NotNull T instance,
                                    @Nullable String qualifier,
                                    boolean primary);

    /**
     * Register a bean instance with a reload callback.
     *
     * @param instance       the instance to register
     * @param qualifier      optional qualifier name
     * @param primary        whether this is a primary bean
     * @param reloadCallback optional reload callback
     * @return the registered instance
     */
    <T> @NotNull T registerInstance(@NotNull T instance,
                                    @Nullable String qualifier,
                                    boolean primary,
                                    @Nullable DependencyReloadCallback reloadCallback);

    /**
     * Register a bean instance for a specific requested type.
     *
     * @param requestedType the type to register for
     * @param instance      the instance to register
     * @param qualifier     optional qualifier name
     * @param primary       whether this is a primary bean
     * @return the registered instance
     */
    <T> @NotNull T registerInstance(@NotNull Class<T> requestedType,
                                    @NotNull T instance,
                                    @Nullable String qualifier,
                                    boolean primary);

    /**
     * Register a bean definition (class-based, lazy instantiation).
     *
     * @param clazz     the class to register
     * @param qualifier optional qualifier name
     * @param primary   whether this is a primary bean
     * @return the created bean definition
     */
    <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> clazz,
                                                   @Nullable String qualifier,
                                                   boolean primary);

    /**
     * Register a bean definition with a reload callback.
     *
     * @param clazz          the class to register
     * @param qualifier      optional qualifier name
     * @param primary        whether this is a primary bean
     * @param reloadCallback optional reload callback
     * @return the created bean definition
     */
    <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> clazz,
                                                   @Nullable String qualifier,
                                                   boolean primary,
                                                   @Nullable DependencyReloadCallback reloadCallback);

    /**
     * Register a bean definition with a resolver (for interfaces or custom instantiation).
     *
     * @param requestedType      the requested type
     * @param implementationType the implementation type
     * @param qualifier          optional qualifier name
     * @param primary            whether this is a primary bean
     * @param resolver           the resolver to create instances
     * @return the created bean definition
     */
    <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> requestedType,
                                                   @NotNull Class<? extends T> implementationType,
                                                   @Nullable String qualifier,
                                                   boolean primary,
                                                   @Nullable DependencyResolveResolver<T> resolver);

    /**
     * Register a bean definition with a resolver and reload callback.
     *
     * @param requestedType      the requested type
     * @param implementationType the implementation type
     * @param qualifier          optional qualifier name
     * @param primary            whether this is a primary bean
     * @param resolver           the resolver to create instances
     * @param reloadCallback     optional reload callback
     * @return the created bean definition
     */
    <T> @NotNull BeanDefinition registerDefinition(@NotNull Class<T> requestedType,
                                                   @NotNull Class<? extends T> implementationType,
                                                   @Nullable String qualifier,
                                                   boolean primary,
                                                   @Nullable DependencyResolveResolver<T> resolver,
                                                   @Nullable DependencyReloadCallback reloadCallback);
}


