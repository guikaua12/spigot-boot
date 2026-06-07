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
package tech.guilhermekaua.spigotboot.inventoryapi.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;

/**
 * Owns the per-viewer Bukkit {@link Inventory} and the slot-to-callback mapping. Implementations
 * apply placeholders to display name and lore at item-set time.
 */
public interface InventoryEditor {

    Inventory getInventory();

    /**
     * Places an item in the given slot, or clears the slot when {@code inventoryItem} is null.
     *
     * @param slot           the slot index
     * @param inventoryItem  the item to place, or {@code null} to clear the slot
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setItem(int slot, InventoryItem inventoryItem);

    /**
     * Places an item in the given slot, using a fallback when {@code inventoryItem} is null.
     *
     * @param slot           the slot index
     * @param inventoryItem  the primary item, or {@code null} to use the fallback
     * @param fallbackItem   the item used when {@code inventoryItem} is null
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setItem(int slot, InventoryItem inventoryItem, InventoryItem fallbackItem);

    /**
     * Clears the given slot.
     *
     * @param slot the slot index
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setEmptyItem(int slot);

    void updateItemStack(int slot);

    void updateAllItemStacks();

    /**
     * Returns the item in the given slot with placeholders applied.
     *
     * @param slot the slot index
     * @return the item stack in the slot, or {@code null} if empty
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    ItemStack getItemStack(int slot);

    ItemCallback getItemCallback(int slot);

}
