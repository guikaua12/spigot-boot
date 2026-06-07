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
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context for component click handlers and {@code View.onClick}: one instance per
 * intercepted {@link InventoryClickEvent}. Main thread, engine-invoked.
 *
 * <p>Cancellation is three-layered (config default, per-component override, then
 * {@link #setCancelled(boolean)} — last writer wins); the safety floor for cross-inventory
 * moves is enforced after handlers regardless of their decision.
 */
@ApiStatus.NonExtendable
public interface SlotClickContext extends ViewContext {

    /**
     * Returns the raw slot id of the clicked container.
     *
     * @return the clicked raw slot
     */
    int slot();

    /**
     * Returns the Bukkit click type of this click.
     *
     * @return the click type
     */
    @NotNull ClickType clickType();

    /**
     * Returns the item on the clicked slot, if any.
     *
     * @return the clicked item, or {@code null} when the slot is empty
     */
    @Nullable ItemStack item();

    /**
     * Returns whether the click landed in the player's own (bottom) inventory. Bottom
     * clicks are always pre-cancelled and delivered only to the view-level {@code onClick}.
     *
     * @return {@code true} for bottom-inventory clicks
     */
    boolean isPlayerInventory();

    /**
     * Overrides the cancellation decision for this click; last writer wins. The safety
     * floor still force-cancels cross-inventory moves after handlers run.
     *
     * @param cancelled whether the underlying event should be cancelled
     */
    void setCancelled(boolean cancelled);

    /**
     * Returns the current cancellation decision: config/component policy applied before
     * handlers run, possibly overturned by {@link #setCancelled(boolean)}.
     *
     * @return {@code true} when the click is currently cancelled
     */
    boolean isCancelled();

    /**
     * Returns the underlying Bukkit event as an escape hatch.
     *
     * @return the raw click event
     */
    @NotNull InventoryClickEvent rawEvent();
}
