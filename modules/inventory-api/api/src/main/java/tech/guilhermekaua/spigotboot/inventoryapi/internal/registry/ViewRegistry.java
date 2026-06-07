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
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of view singletons and their frozen configs, keyed by view class.
 *
 * <p>Discovery-driven registration ({@code initialize(Context)} and the DI constructor)
 * is added in plan task 17; until then views are registered directly via
 * {@link #register(View)}.
 */
@Component
@ApiStatus.Internal
public final class ViewRegistry {

    private final Map<Class<? extends View>, RegisteredView> views = new LinkedHashMap<>();

    /**
     * Creates a registry without discovery support; views are registered directly through
     * {@link #register(View)}.
     */
    public ViewRegistry() {
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
        invokeOnInit(instance, builder);
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

    // onInit is protected on the public View type; the registry dispatches reflectively
    private static void invokeOnInit(View instance, ViewConfigBuilder builder) {
        try {
            Method method = View.class.getDeclaredMethod("onInit", ViewConfigBuilder.class);
            method.setAccessible(true);
            method.invoke(instance, builder);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onInit failed for view " + instance.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onInit for view " + instance.getClass().getName(), ex);
        }
    }
}
