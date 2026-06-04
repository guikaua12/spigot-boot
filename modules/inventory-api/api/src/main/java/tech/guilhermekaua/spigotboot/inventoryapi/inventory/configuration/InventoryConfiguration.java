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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration;

/**
 * Per-inventory tunable configuration. Exposes the periodic update tick rate and whether that
 * update runs on or off the main server thread; subclassable for inventory-specific extension.
 */
public interface InventoryConfiguration {

    /**
     * Sentinel for {@link #tickUpdate()}: periodic updates are not scheduled when the value is
     * {@code <= 0}.
     */
    int TICK_UPDATE_DISABLED = 0;

    int tickUpdate();

    @SuppressWarnings("UnusedReturnValue")
    InventoryConfiguration tickUpdate(int ticks);

    /**
     * Whether the periodic {@link #tickUpdate()} task runs off the main server thread.
     *
     * <p>Defaults to {@code false} (the task runs on the main thread), which is the only mode in
     * which the Bukkit calls made during an update — item edits, {@code player.updateInventory()},
     * placeholder resolution — are safe. Enable async only for inventories whose update logic and
     * placeholders are genuinely thread-safe; most Bukkit and PlaceholderAPI calls are not. Updates
     * triggered by clicks always run on the main thread regardless of this flag.
     *
     * @return {@code true} if periodic updates should be dispatched asynchronously
     */
    boolean tickAsync();

    /**
     * Sets whether the periodic update task runs asynchronously. See {@link #tickAsync()} for the
     * thread-safety caveats.
     *
     * @param async {@code true} to dispatch periodic updates off the main thread
     * @return this configuration, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    InventoryConfiguration tickAsync(boolean async);

}
