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
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral scheduling. On Folia every task is bound to a region (an entity's, a
 * location's, or the global region); on legacy Spigot/Paper all variants fall back to the
 * single main thread. Obtain the configured instance by injecting this type.
 *
 * <p>Times are in server ticks (20 per second), matching the legacy scheduler.
 */
public interface PlatformScheduler {

    /**
     * Runs a task on the thread owning the entity's region (Folia) or the main thread (legacy).
     *
     * @param entity  the entity whose region owns the task
     * @param task    the work to run
     * @param retired run instead of {@code task} if the entity is removed before it fires
     *                (Folia only); ignored on legacy
     * @return a cancellable handle
     */
    @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    /**
     * Runs a task on the thread owning the entity's region (Folia) or the main thread (legacy),
     * after a delay.
     *
     * @param entity      the entity whose region owns the task
     * @param task        the work to run
     * @param retired     run instead of {@code task} if the entity is removed before it fires
     *                    (Folia only); ignored on legacy
     * @param delayTicks  delay in ticks before the task fires; clamped to at least 1
     * @return a cancellable handle
     */
    @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks);

    /**
     * Runs a task repeatedly on the thread owning the entity's region (Folia) or the main thread
     * (legacy).
     *
     * @param entity      the entity whose region owns the task
     * @param task        the work to run each period
     * @param retired     run instead of {@code task} if the entity is removed before it fires
     *                    (Folia only); ignored on legacy
     * @param delayTicks  delay in ticks before the first execution; clamped to at least 1
     * @param periodTicks interval in ticks between subsequent executions; clamped to at least 1
     * @return a cancellable handle
     */
    @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks);

    /**
     * Runs a task on the thread owning the given location's region (Folia) or the main thread
     * (legacy).
     *
     * @param location the location whose region owns the task
     * @param task     the work to run
     * @return a cancellable handle
     */
    @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task);

    /**
     * Runs a task on the thread owning the given location's region (Folia) or the main thread
     * (legacy), after a delay.
     *
     * @param location   the location whose region owns the task
     * @param task       the work to run
     * @param delayTicks delay in ticks before the task fires; clamped to at least 1
     * @return a cancellable handle
     */
    @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks);

    /**
     * Runs a task on the global region thread (Folia) or the main thread (legacy).
     *
     * @param task the work to run
     * @return a cancellable handle
     */
    @NotNull PlatformTask runGlobal(@NotNull Runnable task);

    /**
     * Runs a task on the global region thread (Folia) or the main thread (legacy), after a delay.
     *
     * @param task       the work to run
     * @param delayTicks delay in ticks before the task fires; clamped to at least 1
     * @return a cancellable handle
     */
    @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks);

    /**
     * @return {@code true} if the current thread owns the entity's region (Folia) or is the
     * main thread (legacy) — i.e. it is safe to touch the entity now.
     */
    boolean ownsRegion(@NotNull Entity entity);

    /**
     * @param location the location to check
     * @return {@code true} if the current thread owns the given location's region (Folia) or is
     * the main thread (legacy) — i.e. it is safe to touch that location now.
     */
    boolean ownsRegion(@NotNull Location location);
}
