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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link SettleDispatcher}: settles route to the viewer's region thread (Folia) or
 * the main thread (legacy Spigot/Paper) via the injected {@link PlatformScheduler}.
 *
 * <p>Routing rules:
 * <ol>
 *   <li>No viewer ({@link PageRequest#viewer()} is {@code null}) — engine-external test
 *       usage: settle runs inline on the completing thread.</li>
 *   <li>The calling thread already owns the viewer's region — settle runs inline.</li>
 *   <li>Otherwise — the settle is dispatched to the viewer's entity scheduler.</li>
 * </ol>
 *
 * <p>Dispatch order is FIFO: inline settles run immediately; the scheduler runs same-tick
 * tasks in submission order. {@link AsyncPageSource} relies on this — a request's timeout
 * settle must reach the dispatcher before the cancellation settle it triggers.
 *
 * <p>A settle that is rejected because the owning plugin is disabling
 * ({@link IllegalPluginAccessException}) is dropped with a warning: the scheduler will not
 * accept new tasks at that point and the inventory is about to be closed anyway.
 */
public final class BukkitSettleDispatcher implements SettleDispatcher {

    private static final Logger LOGGER = Logger.getLogger(BukkitSettleDispatcher.class.getName());

    private final PlatformScheduler scheduler;

    /**
     * Creates the dispatcher.
     *
     * @param scheduler the platform scheduler used to route settles to the viewer's region
     */
    public BukkitSettleDispatcher(@NotNull PlatformScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        Player viewer = request.viewer();
        if (viewer == null) {
            // engine-external test usage: settle inline on the completing thread
            task.run();
            return;
        }
        if (scheduler.ownsRegion(viewer)) {
            task.run();
            return;
        }
        try {
            scheduler.runOnEntity(viewer, task, null);
        } catch (IllegalPluginAccessException e) {
            // thrown inside whenComplete or a timeout task; swallow so the completing thread
            // does not blow up — the session is about to be closed during plugin disable
            LOGGER.log(Level.WARNING, "Dropped a page-load settle: the owning plugin is disabled.", e);
        }
    }
}
