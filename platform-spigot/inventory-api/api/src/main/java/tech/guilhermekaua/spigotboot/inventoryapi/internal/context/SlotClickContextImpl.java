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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Context for component click handlers and {@code View.onClick}: wraps one Bukkit click
 * event. The cancellation decision lives on an internal flag seeded with the pre-cancel
 * policy; the routing phase applies the final decision to the raw event after handlers run.
 */
@ApiStatus.Internal
public final class SlotClickContextImpl extends AbstractViewContext implements SlotClickContext {

    private final InventoryClickEvent event;
    private final boolean playerInventory;
    private boolean cancelled;

    /**
     * Creates the context for one intercepted click.
     *
     * @param session         the clicked session
     * @param engine          the engine dispatching the click
     * @param event           the underlying Bukkit event
     * @param playerInventory whether the click landed in the player's own (bottom) inventory
     * @param preCancelled    the cancellation decision computed from config and component policy
     */
    public SlotClickContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine,
                                @NotNull InventoryClickEvent event, boolean playerInventory,
                                boolean preCancelled) {
        super(session, engine);
        this.event = Objects.requireNonNull(event, "event");
        this.playerInventory = playerInventory;
        this.cancelled = preCancelled;
    }

    @Override
    public int slot() {
        return event.getRawSlot();
    }

    @Override
    public @NotNull ClickType clickType() {
        return event.getClick();
    }

    @Override
    public @Nullable ItemStack item() {
        return event.getCurrentItem();
    }

    @Override
    public boolean isPlayerInventory() {
        return playerInventory;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public @NotNull InventoryClickEvent rawEvent() {
        return event;
    }
}
