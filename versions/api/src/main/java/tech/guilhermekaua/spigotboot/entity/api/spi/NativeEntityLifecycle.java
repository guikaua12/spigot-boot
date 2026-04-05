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

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;

/**
 * Internal bridge used by version adapters and generated native subclasses.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface NativeEntityLifecycle<T extends LivingEntity> {

    /**
     * Binds the Bukkit entity wrapper after the native entity has been inserted into the world.
     *
     * @param bukkitEntity the Bukkit entity wrapper
     */
    void bind(@NotNull T bukkitEntity);

    /**
     * Invokes runtime-managed spawn lifecycle callbacks.
     */
    void onSpawn();

    /**
     * Invokes runtime-managed per-tick lifecycle callbacks.
     */
    void onNativeTick();

    /**
     * Invokes runtime-managed removal lifecycle callbacks.
     */
    void onNativeRemove();

    /**
     * Returns the public plugin-facing entity handle.
     *
     * @return the public handle
     */
    @NotNull CustomEntityHandle<T> handle();
}
