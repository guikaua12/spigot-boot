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
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;

import java.util.Objects;

/**
 * Immutable registration record of one view class: the singleton instance and the frozen
 * config its {@code onInit} produced at registration time.
 */
@ApiStatus.Internal
public final class RegisteredView {

    private final Class<? extends View> type;
    private final View instance;
    private final ViewConfig config;

    /**
     * Creates the registration record.
     *
     * @param type     the registered view class
     * @param instance the view singleton
     * @param config   the frozen registration-time config
     */
    public RegisteredView(@NotNull Class<? extends View> type, @NotNull View instance,
                          @NotNull ViewConfig config) {
        this.type = Objects.requireNonNull(type, "type");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Returns the registered view class.
     *
     * @return the view class
     */
    public @NotNull Class<? extends View> type() {
        return type;
    }

    /**
     * Returns the view singleton.
     *
     * @return the view instance
     */
    public @NotNull View instance() {
        return instance;
    }

    /**
     * Returns the frozen registration-time config; per-open overrides are merged onto a
     * copy by the open phase, never onto this instance.
     *
     * @return the immutable view config
     */
    public @NotNull ViewConfig config() {
        return config;
    }
}
