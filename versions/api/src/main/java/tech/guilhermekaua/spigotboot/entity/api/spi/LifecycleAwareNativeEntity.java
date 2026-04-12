/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.api.spi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by generated native subclasses so version adapters can bind runtime lifecycle delegates.
 *
 * @since 2.0.2
 */
public interface LifecycleAwareNativeEntity {

    /**
     * Binds the runtime lifecycle delegate to this native entity instance.
     *
     * @param lifecycle the runtime lifecycle delegate
     */
    void spigotBootBindLifecycle(@NotNull NativeEntityLifecycle<?> lifecycle);

    /**
     * Returns the currently bound runtime lifecycle, or {@code null} when none is bound.
     *
     * @return the bound runtime lifecycle, or {@code null}
     */
    @Nullable NativeEntityLifecycle<?> spigotBootGetLifecycle();

    /**
     * Invokes the original vanilla implementation for the supplied logical hook.
     *
     * @param hookName the logical hook name
     * @param arguments the translated native arguments
     * @return the vanilla result, or {@code null} for void hooks
     */
    @Nullable Object spigotBootInvokeBase(@NotNull String hookName, @Nullable Object[] arguments);
}
