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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A live reference to a config value that updates on reload.
 * <p>
 * Thread-safe wrapper using volatile + copy-on-write.
 *
 * @param <T> the config type
 */
public interface ConfigRef<T> extends Supplier<T> {

    /**
     * Gets the current config value.
     *
     * @return the current value
     */
    @Override
    @NotNull T get();

    /**
     * Adds a listener for config changes.
     *
     * @param listener the listener to add
     */
    void addListener(@NotNull Consumer<T> listener);

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(@NotNull Consumer<T> listener);

    /**
     * Checks if the config has been loaded at least once.
     *
     * @return true if loaded
     */
    boolean isLoaded();

    /**
     * Forces a reload of this config.
     */
    void reload();

    /**
     * Gets the config class this ref wraps.
     *
     * @return the config class
     */
    @NotNull Class<T> getConfigClass();
}
