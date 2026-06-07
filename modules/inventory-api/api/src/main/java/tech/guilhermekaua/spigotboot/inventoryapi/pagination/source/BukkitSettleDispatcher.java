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

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link SettleDispatcher}: settles always route to the main server thread — inline
 * when the load already completed there, otherwise through the Bukkit scheduler on behalf of
 * the request's owning plugin. A request without a plugin (engine-external test usage only)
 * settles inline on the completing thread.
 *
 * <p>Dispatch order is FIFO: inline settles run immediately and the Bukkit scheduler runs
 * same-tick tasks in submission order. {@link AsyncPageSource} relies on this — a request's
 * timeout settle must reach the dispatcher before the cancellation settle it triggers, so the
 * at-most-once check discards the latter.
 *
 * <p>A settle completing while the owning plugin is disabling is dropped with a warning: the
 * scheduler rejects new tasks at that point and the inventory is about to be closed by the
 * shutdown anyway.
 */
public final class BukkitSettleDispatcher implements SettleDispatcher {

    private static final Logger LOGGER = Logger.getLogger(BukkitSettleDispatcher.class.getName());

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        if (request.plugin() == null || Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(request.plugin(), task);
        } catch (IllegalPluginAccessException e) {
            // thrown inside whenComplete or a timeout task this would otherwise vanish into
            // an unobserved future
            LOGGER.log(Level.WARNING, "Dropped a page-load settle: the owning plugin is disabled.", e);
        }
    }
}
