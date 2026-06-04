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
package tech.guilhermekaua.spigotboot.inventoryapi.event.impl;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.event.CustomInventoryEvent;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

/**
 * Wraps Bukkit's {@link InventoryClickEvent} with the inventory-api {@link Viewer} and helper
 * methods for refreshing the clicked slot or the entire inventory.
 */
@Getter
public final class CustomInventoryClickEvent extends CustomInventoryEvent implements Cancellable {

    private final InventoryClickEvent primaryEvent;

    private final Inventory clickedInventory;
    private final ItemStack itemStack;
    private final ClickType clickType;
    private final int slot;

    public CustomInventoryClickEvent(Viewer viewer, InventoryClickEvent primaryEvent) {
        super(viewer);
        this.primaryEvent = primaryEvent;
        this.clickedInventory = primaryEvent.getClickedInventory();
        this.itemStack = primaryEvent.getCurrentItem();
        this.clickType = primaryEvent.getClick();
        this.slot = primaryEvent.getRawSlot();
    }

    public void updateItemStack() {
        InventoryEditor inventoryEditor = this.getViewer().getEditor();
        inventoryEditor.updateItemStack(slot);
    }

    public void updateInventory() {
        CustomInventory customInventory = this.getCustomInventory();
        customInventory.updateInventory(this.getPlayer());
    }

    @Override
    public boolean isCancelled() {
        return this.primaryEvent.isCancelled();
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.primaryEvent.setCancelled(cancel);
    }

}
