/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Hook context exposed to {@link EntityController#onInventoryChange(EntityInventoryChangeContext)}.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class EntityInventoryChangeContext<T extends LivingEntity> extends AbstractEntityHookContext<T, Void> {
    private final EntityEquipmentSlot slot;
    private ItemStack previousItem;
    private ItemStack newItem;

    /**
     * Creates a new inventory-change hook context.
     *
     * @param entity the controlled entity
     * @param base the vanilla base invoker
     * @param slot the logical slot
     * @param previousItem the previous item, or {@code null}
     * @param newItem the new item, or {@code null}
     */
    public EntityInventoryChangeContext(
            @NotNull ControlledEntity<T> entity,
            @NotNull EntityBaseInvoker<Void> base,
            @NotNull EntityEquipmentSlot slot,
            @Nullable ItemStack previousItem,
            @Nullable ItemStack newItem
    ) {
        super(entity, base);
        this.slot = Objects.requireNonNull(slot, "slot cannot be null");
        this.previousItem = previousItem;
        this.newItem = newItem;
    }

    /**
     * Returns the logical slot being updated.
     *
     * @return the logical slot being updated
     */
    public @NotNull EntityEquipmentSlot slot() {
        return slot;
    }

    /**
     * Returns the previous item, or {@code null} when the slot was empty.
     *
     * @return the previous item, or {@code null}
     */
    public @Nullable ItemStack previousItem() {
        return previousItem;
    }

    /**
     * Replaces the previous item passed to the vanilla base call.
     *
     * @param previousItem the replacement previous item
     */
    public void setPreviousItem(@Nullable ItemStack previousItem) {
        this.previousItem = previousItem;
    }

    /**
     * Returns the new item, or {@code null} when the slot is being cleared.
     *
     * @return the new item, or {@code null}
     */
    public @Nullable ItemStack newItem() {
        return newItem;
    }

    /**
     * Replaces the new item passed to the vanilla base call.
     *
     * @param newItem the replacement new item
     */
    public void setNewItem(@Nullable ItemStack newItem) {
        this.newItem = newItem;
    }
}
