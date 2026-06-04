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
package tech.guilhermekaua.spigotboot.inventoryapi.editor.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.update.ItemUpdateCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public final class InventoryEditorImpl implements InventoryEditor {

    private final Inventory inventory;
    private final PlaceholderApplier placeholderApplier;
    // ConcurrentHashMap so a tickAsync update can iterate the callbacks while a main-thread click
    // mutates them without a ConcurrentModificationException; item callbacks are never null
    private final Map<Integer, ItemCallback> inventoryCallbackMap = new ConcurrentHashMap<>();

    @Override
    public void setItem(int slot, InventoryItem inventoryItem) {
        if (inventoryItem == null) {
            setEmptyItem(slot);
            return;
        }

        final ItemStack itemStack = applyPlaceholders(inventoryItem.getItemStack());

        this.inventory.setItem(slot, itemStack);
        this.inventoryCallbackMap.put(slot, inventoryItem.getItemCallback());
    }

    @Override
    public void setItem(int slot, InventoryItem inventoryItem, InventoryItem fallbackItem) {
        if (inventoryItem == null) {
            if (fallbackItem == null) {
                setEmptyItem(slot);
                return;
            }

            final ItemStack itemStack = applyPlaceholders(fallbackItem.getItemStack());
            this.inventory.setItem(slot, itemStack);
            this.inventoryCallbackMap.put(slot, fallbackItem.getItemCallback());
            return;
        }

        final ItemStack itemStack = applyPlaceholders(inventoryItem.getItemStack());
        this.inventory.setItem(slot, itemStack);
        this.inventoryCallbackMap.put(slot, inventoryItem.getItemCallback());
    }

    @Override
    public void setEmptyItem(int slot) {
        this.inventory.setItem(slot, null);
        this.inventoryCallbackMap.remove(slot);
    }

    @Override
    public void fillPage(List<InventoryItem> inventoryItems, InventoryLayout layout, Pagination<?> pagination) {
        List<InventorySlot> slots = layout.getSlots();

        for (int i = 0; i < slots.size(); i++) {
            int itemSlot = slots.get(i).getSlot();
            InventoryItem item = inventoryItems.get(i);

            setItem(itemSlot, item, pagination.getFallbackItem());
        }
    }

    @Override
    public void updateItemStack(int slot) {
        ItemCallback itemCallback = getItemCallback(slot);
        if (itemCallback == null) return;

        updateItemStack(slot, itemCallback);
    }

    @Override
    public void updateAllItemStacks() {
        for (Map.Entry<Integer, ItemCallback> entry : inventoryCallbackMap.entrySet()) {
            updateItemStack(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public ItemStack getItemStack(int slot) {
        return applyPlaceholders(this.inventory.getItem(slot));
    }

    @Override
    public ItemCallback getItemCallback(int slot) {
        return this.inventoryCallbackMap.get(slot);
    }

    private void updateItemStack(int slot, ItemCallback itemCallback) {
        ItemUpdateCallback updateCallback = itemCallback.getUpdateCallback();
        if (updateCallback == null) return;

        ItemStack itemStack = getItemStack(slot);
        updateCallback.accept(itemStack);

        this.inventory.setItem(slot, itemStack);
    }

    private ItemStack applyPlaceholders(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        itemStack = itemStack.clone();

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        Player player = firstViewer();

        itemMeta.setDisplayName(placeholderApplier.apply(player, itemMeta.getDisplayName()));
        itemMeta.setLore(placeholderApplier.applyAll(player, itemMeta.getLore()));

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private Player firstViewer() {
        List<HumanEntity> viewers = this.inventory.getViewers();
        if (viewers.isEmpty()) {
            return null;
        }

        HumanEntity entity = viewers.get(0);
        return entity instanceof Player ? (Player) entity : null;
    }

}
