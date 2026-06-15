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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Legacy {@link PlatformScheduler}: every variant maps to {@link org.bukkit.scheduler.BukkitScheduler}
 * and runs on the single main thread. {@code retired} callbacks have no legacy equivalent and are
 * ignored. Selected on Spigot / Paper builds that lack the Folia scheduler API.
 */
public final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public BukkitPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        return Bukkit.isPrimaryThread();
    }
}
