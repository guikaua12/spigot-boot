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
package tech.guilhermekaua.spigotboot.config.reload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default implementation of ConfigRef.
 *
 * @param <T> the config type
 */
public class DefaultConfigRef<T> implements ConfigRef<T> {

    private final Class<T> configClass;
    private final Supplier<T> reloader;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;
    private volatile boolean loaded = false;

    /**
     * Creates a new ConfigRef.
     *
     * @param configClass  the config class
     * @param initialValue the initial value
     * @param reloader     the reloader supplier
     */
    public DefaultConfigRef(
            @NotNull Class<T> configClass,
            @Nullable T initialValue,
            @NotNull Supplier<T> reloader
    ) {
        this.configClass = Objects.requireNonNull(configClass, "configClass cannot be null");
        this.reloader = Objects.requireNonNull(reloader, "reloader cannot be null");
        this.value = initialValue;
        this.loaded = initialValue != null;
    }

    @Override
    @NotNull
    public T get() {
        T current = value;
        if (current == null) {
            throw new IllegalStateException("Config not loaded: " + configClass.getName());
        }
        return current;
    }

    @Override
    public void addListener(@NotNull Consumer<T> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NotNull Consumer<T> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.remove(listener);
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public void reload() {
        T newValue = reloader.get();
        this.value = newValue;
        this.loaded = true;

        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(newValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    @NotNull
    public Class<T> getConfigClass() {
        return configClass;
    }

    /**
     * Updates the value without triggering reload.
     *
     * @param newValue the new value
     */
    public void update(@NotNull T newValue) {
        Objects.requireNonNull(newValue, "newValue cannot be null");
        this.value = newValue;
        this.loaded = true;

        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(newValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
