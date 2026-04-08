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
package tech.guilhermekaua.spigotboot.entity.runtime.lifecycle;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;

import java.util.Collection;

/**
 * Version-owned binding between native hook signatures and the shared runtime dispatcher.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface NativeHookBinder<T extends Entity> {

    /**
     * Resolves the native methods that should be overridden for the supplied native type.
     *
     * @param nativeType the native type being hooked
     * @return the native hook specs
     */
    @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType);

    /**
     * Dispatches one logical native hook into the shared runtime.
     *
     * @param controlledEntity the shared runtime handle
     * @param nativeEntity the generated native entity instance
     * @param hookName the logical hook name
     * @param arguments the raw native arguments
     * @return the translated hook result, or {@code null} for void hooks
     */
    @Nullable Object dispatch(
            @NotNull AbstractRuntimeControlledEntity<T> controlledEntity,
            @NotNull LifecycleAwareNativeEntity nativeEntity,
            @NotNull String hookName,
            @Nullable Object[] arguments
    );
}
