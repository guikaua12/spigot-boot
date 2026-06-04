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
package tech.guilhermekaua.spigotboot.inventoryapi.title;

import org.bukkit.entity.Player;

/**
 * {@link TitleUpdater} backed by the public Bukkit API
 * ({@code Player#getOpenInventory()#setTitle(String)}). Works on Spigot/Paper 1.20 and later,
 * which is when {@code InventoryView#setTitle} became part of the public API.
 *
 * <p>For older servers the framework dispatches to a per-version NMS implementation via the
 * {@code spigot-boot-inventory-api-nms} module; on 1.20+ the selector uses the public Bukkit
 * API instead.
 */
public class BukkitOpenInventoryTitleUpdater implements TitleUpdater {
    @Override
    public void update(Player player, String title) {
        player.getOpenInventory().setTitle(title);
    }
}
