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
 * Updates the title of the inventory currently open for a player without forcing the player to
 * close and reopen the GUI.
 *
 * <p>The default implementation, {@link BukkitOpenInventoryTitleUpdater}, uses
 * {@code Player#getOpenInventory()#setTitle(String)} which works on Spigot/Paper 1.20+.
 * Users targeting older server versions can register their own {@link TitleUpdater} bean
 * (the auto-configuration's factory is guarded by {@code @ConditionalOnMissingBean}).
 *
 * <p>Calling {@link #update(Player, String)} when no inventory is currently open is a no-op
 * on the Bukkit-API path; for that case there is nothing to update.
 */
@FunctionalInterface
public interface TitleUpdater {
    /**
     * Replaces the title shown on the inventory the player has open.
     *
     * @param player the viewer whose open inventory should be retitled
     * @param title  the new title text
     */
    void update(Player player, String title);
}
