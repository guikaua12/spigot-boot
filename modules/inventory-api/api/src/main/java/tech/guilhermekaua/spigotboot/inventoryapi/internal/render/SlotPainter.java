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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.render;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.Objects;

/**
 * Writes item stacks into container slots, applying placeholders to display name and lore
 * at paint time on a clone of the given item (mirrors the 2.x {@code InventoryEditorImpl}
 * placeholder behavior); the caller's item is never mutated.
 */
@Component
@ApiStatus.Internal
public final class SlotPainter {

    private final PlaceholderApplier placeholderApplier;

    /**
     * @param placeholderApplier the applier resolving placeholder tokens per player
     */
    public SlotPainter(@NotNull PlaceholderApplier placeholderApplier) {
        this.placeholderApplier = Objects.requireNonNull(placeholderApplier, "placeholderApplier");
    }

    /**
     * Paints the slot: a null item clears it; otherwise a clone of the item is written,
     * with placeholders applied to its display name and lore when requested.
     *
     * @param player            the viewer whose context resolves player-scoped placeholders
     * @param inventory         the container to write into
     * @param slot              the raw slot to paint
     * @param item              the item to paint, or null to clear the slot
     * @param applyPlaceholders whether placeholders should be applied to the item's meta
     */
    public void paint(@NotNull Player player, @NotNull Inventory inventory, int slot,
                      @Nullable ItemStack item, boolean applyPlaceholders) {
        if (item == null) {
            inventory.setItem(slot, null);
            return;
        }

        // clone so placeholder application never mutates the caller's item
        ItemStack copy = item.clone();
        if (applyPlaceholders) {
            ItemMeta meta = copy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(placeholderApplier.apply(player, meta.getDisplayName()));
                meta.setLore(placeholderApplier.applyAll(player, meta.getLore()));
                copy.setItemMeta(meta);
            }
        }

        inventory.setItem(slot, copy);
    }
}
