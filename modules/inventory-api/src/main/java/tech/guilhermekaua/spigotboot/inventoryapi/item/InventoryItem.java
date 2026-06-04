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
package tech.guilhermekaua.spigotboot.inventoryapi.item;

import lombok.Data;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.event.impl.CustomInventoryClickEvent;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.update.ItemUpdateCallback;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pairs a Bukkit {@link ItemStack} with its click and update callbacks. Constructed via the
 * static {@link #of(ItemStack)} factory and decorated through fluent
 * {@link #callback(ClickType, Consumer)}, {@link #defaultCallback(Consumer)} and
 * {@link #updateCallback(ItemUpdateCallback)} setters.
 */
@Data(staticConstructor = "of")
public final class InventoryItem {

    private final ItemStack itemStack;
    private final ItemCallback itemCallback = new ItemCallback();

    public InventoryItem callback(ClickType clickType, Consumer<CustomInventoryClickEvent> eventConsumer) {
        this.itemCallback.callback(clickType, eventConsumer);
        return this;
    }

    public InventoryItem defaultCallback(Consumer<CustomInventoryClickEvent> eventConsumer) {
        return this.callback(null, eventConsumer);
    }

    public InventoryItem updateCallback(ItemUpdateCallback updateCallback) {
        this.itemCallback.setUpdateCallback(updateCallback);
        return this;
    }

    public static InventoryItem of(Supplier<ItemStack> supplier) {
        return of(supplier.get());
    }

}
