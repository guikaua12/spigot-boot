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
package tech.guilhermekaua.spigotboot.inventoryapi;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;

/**
 * Bootstraps the inventory-api view engine: initializes the {@link ViewRegistry}, which
 * discovers and registers every
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView}-annotated
 * {@link View} subclass under the host plugin's base package and instantiates each through
 * the dependency manager.
 */
public final class InventoryApiModule implements Module {

    @Inject
    private ViewRegistry viewRegistry;

    @Override
    public void onInitialize(Context context) throws Exception {
        viewRegistry.initialize(context);
    }

    /**
     * Shuts the shared pagination timeout scheduler down when the host plugin disables.
     * Open sessions are already closed when this runs: {@code ViewListener.onPluginDisable}
     * reacts to Bukkit's {@code PluginDisableEvent}, which fires before the context destroys
     * its beans and invokes this hook, so no session can still be waiting on a timeout. The
     * scheduler is recreated lazily on the next timeout-bearing request.
     */
    @OnDisable
    public void onDisable() {
        AsyncPageSource.shutdownSharedTimeoutScheduler();
    }
}
