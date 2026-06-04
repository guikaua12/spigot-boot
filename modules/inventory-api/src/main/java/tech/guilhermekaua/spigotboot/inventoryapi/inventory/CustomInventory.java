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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Top-level abstraction for an inventory definition. Concrete subclasses (typically extending
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl}) describe
 * a GUI's title, size, configuration and per-render lifecycle hooks.
 *
 * <p>Discovered automatically by the module when annotated with
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory}.
 */
public interface CustomInventory {

    @NotNull
    String getTitle();

    int getSize();

    @NotNull <T extends InventoryConfiguration> T getConfiguration();

    void defaultOpenInventory(Player player, Viewer viewer, Consumer<Viewer> viewerConsumer);

    <T extends InventoryConfiguration> void configuration(@NotNull Consumer<T> consumer);

    void updateInventory(@NotNull Player player);

}
