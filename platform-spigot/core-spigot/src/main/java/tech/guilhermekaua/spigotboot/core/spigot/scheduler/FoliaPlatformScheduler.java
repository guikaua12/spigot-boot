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

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Folia / modern-Paper {@link PlatformScheduler}. Region/entity/global tasks land on the owning
 * region thread. The scheduler accessors on {@link Bukkit}/{@link Entity} are invoked reflectively
 * (so the 1.8.8 animal-sniffer net over {@code org.bukkit.*} stays intact); the scheduler
 * interfaces themselves are used with normal typed calls. Instantiated only when the Folia
 * scheduler API is present (see {@link PlatformSchedulers}).
 */
public final class FoliaPlatformScheduler implements PlatformScheduler {

    private static final Method GET_REGION_SCHEDULER;
    private static final Method GET_GLOBAL_SCHEDULER;
    private static final Method IS_OWNED_ENTITY;
    private static final Method IS_OWNED_LOCATION;
    private static final Method ENTITY_GET_SCHEDULER;

    static {
        try {
            GET_REGION_SCHEDULER = Bukkit.class.getMethod("getRegionScheduler");
            GET_GLOBAL_SCHEDULER = Bukkit.class.getMethod("getGlobalRegionScheduler");
            IS_OWNED_ENTITY = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            IS_OWNED_LOCATION = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            ENTITY_GET_SCHEDULER = Entity.class.getMethod("getScheduler");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Folia scheduler API expected but not found", e);
        }
    }

    private final Plugin plugin;

    public FoliaPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    private EntityScheduler entityScheduler(Entity entity) {
        try {
            return (EntityScheduler) ENTITY_GET_SCHEDULER.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private RegionScheduler regionScheduler() {
        try {
            return (RegionScheduler) GET_REGION_SCHEDULER.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private GlobalRegionScheduler globalScheduler() {
        try {
            return (GlobalRegionScheduler) GET_GLOBAL_SCHEDULER.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        ScheduledTask scheduled = entityScheduler(entity).run(plugin, st -> task.run(), retired);
        return scheduled == null ? CancelledPlatformTask.INSTANCE : new FoliaPlatformTask(scheduled);
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        ScheduledTask scheduled = entityScheduler(entity).runDelayed(plugin, st -> task.run(), retired, Math.max(1L, delayTicks));
        return scheduled == null ? CancelledPlatformTask.INSTANCE : new FoliaPlatformTask(scheduled);
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = entityScheduler(entity).runAtFixedRate(plugin, st -> task.run(), retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return scheduled == null ? CancelledPlatformTask.INSTANCE : new FoliaPlatformTask(scheduled);
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        return new FoliaPlatformTask(regionScheduler().run(plugin, location, scheduled -> task.run()));
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return new FoliaPlatformTask(regionScheduler().runDelayed(plugin, location, scheduled -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        return new FoliaPlatformTask(globalScheduler().run(plugin, scheduled -> task.run()));
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return new FoliaPlatformTask(globalScheduler().runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        try {
            return (boolean) IS_OWNED_ENTITY.invoke(null, entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        try {
            return (boolean) IS_OWNED_LOCATION.invoke(null, location);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
