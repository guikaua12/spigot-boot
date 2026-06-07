/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry of view singletons and their frozen configs, keyed by view class.
 *
 * <p>Boot-time discovery and dependency-manager instantiation happen in
 * {@link #initialize(Context)}, mirroring the 2.x {@code InventoryRegistry} bootstrap;
 * views can also be registered directly via {@link #register(View)}.
 */
@Component
@ApiStatus.Internal
public final class ViewRegistry {
    private static final Logger LOGGER = Logger.getLogger(ViewRegistry.class.getName());

    private final Map<Class<? extends View>, RegisteredView> views = new LinkedHashMap<>();

    private final @Nullable ViewDiscoveryService discoveryService;

    /**
     * Creates a registry without discovery support; views are registered directly through
     * {@link #register(View)}.
     */
    public ViewRegistry() {
        this.discoveryService = null;
    }

    /**
     * Creates the registry with discovery support.
     *
     * @param discoveryService the discovery service used by {@link #initialize(Context)}
     */
    @Inject
    public ViewRegistry(@NotNull ViewDiscoveryService discoveryService) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService cannot be null.");
    }

    /**
     * Discovers {@code @RegisterView} classes under the host plugin's base package, instantiates
     * each through the dependency manager and registers it. A view that fails to instantiate or
     * register is logged SEVERE and skipped; the remaining views still register.
     *
     * @param context the host plugin's application context
     * @throws Exception when context access fails
     */
    public void initialize(@NotNull Context context) throws Exception {
        ViewDiscoveryService discovery = this.discoveryService != null
                ? this.discoveryService
                : context.getBean(ViewDiscoveryService.class);
        if (discovery == null) {
            throw new IllegalStateException("ViewDiscoveryService is not available.");
        }

        String basePackage = context.getPlugin().getMainClass().getPackage().getName();
        Set<Class<? extends View>> classes = discovery.discoverFromPackage(basePackage);
        DependencyManager dependencyManager = context.getDependencyManager();

        int registered = 0;
        for (Class<? extends View> viewClass : classes) {
            try {
                registerDiscoveredView(viewClass, dependencyManager);
                registered++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to register view " + viewClass.getName(), ex);
            }
        }

        LOGGER.log(Level.INFO, "Registered {0} views.", registered);
    }

    private void registerDiscoveredView(
            Class<? extends View> viewClass,
            DependencyManager dependencyManager
    ) throws Exception {
        Constructor<?> constructor = dependencyManager.findInjectConstructor(viewClass);
        if (constructor == null) {
            throw new IllegalStateException("No injectable constructor found for view: " + viewClass.getName());
        }

        Object[] constructorArguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        Object rawInstance = constructor.newInstance(constructorArguments);

        BeanDefinition definition = new BeanDefinition(
                viewClass,
                viewClass,
                viewClass.getName() + "#view",
                false,
                null,
                null
        );
        View view = (View) dependencyManager.initializeBean(definition, rawInstance);
        injectSuperclassDependencies(dependencyManager, viewClass, view);

        dependencyManager.registerDependency(
                view,
                BeanUtils.getQualifier(viewClass),
                BeanUtils.getIsPrimary(viewClass)
        );
        register(view);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectSuperclassDependencies(
            DependencyManager dependencyManager,
            Class<? extends View> viewClass,
            View instance
    ) {
        for (Class type = viewClass.getSuperclass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            dependencyManager.injectDependencies(type, instance);
        }
    }

    /**
     * Registers a view instance directly: freezes its token table, runs {@code onInit} once and
     * validates the resulting config.
     *
     * @param instance the view singleton to register
     * @throws ViewConfigurationException when the built config violates the validation rules
     * @throws IllegalStateException      when the view class is already registered
     */
    public void register(@NotNull View instance) {
        Objects.requireNonNull(instance, "instance");
        Class<? extends View> type = instance.getClass();
        if (views.containsKey(type)) {
            throw new IllegalStateException("view " + type.getName() + " is already registered");
        }
        instance.tokenTable().freeze();
        ViewConfigBuilder builder = new ViewConfigBuilder();
        HandlerInvoker.invoke(HandlerInvoker.ON_INIT, instance, builder);
        ViewConfig config = builder.build();
        views.put(type, new RegisteredView(type, instance, config));
    }

    /**
     * Looks up the registration of a view class.
     *
     * @param type the view class to resolve
     * @return the registration, or empty when the class is not registered
     */
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type) {
        return Optional.ofNullable(views.get(type));
    }

    /**
     * Returns every registration in registration order.
     *
     * @return all registrations, unmodifiable
     */
    public @NotNull Collection<RegisteredView> all() {
        return Collections.unmodifiableCollection(views.values());
    }
}
