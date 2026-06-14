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
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class ItemUtils {
    public static ItemStack getHeadByName(String name) {
        Material material = Material.getMaterial("SKULL_ITEM");
        if (material == null) {
            // Most likely 1.13 materials
            material = Material.getMaterial("PLAYER_HEAD");
        }
        final ItemStack head = new ItemStack(material, 1, (short) 3);
        if (name == null || name.isEmpty()) {
            return head;
        }
        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwner(name);

        head.setItemMeta(headMeta);
        return head;
    }

    public static ItemStack getHeadByUuid(UUID uuid) {
        Material material = Material.getMaterial("SKULL_ITEM");
        if (material == null) {
            // Most likely 1.13 materials
            material = Material.getMaterial("PLAYER_HEAD");
        }
        final ItemStack head = new ItemStack(material, 1, (short) 3);
        if (uuid == null) {
            return head;
        }
        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        try {
            headMeta.setOwningPlayer(player);
        } catch (NoSuchMethodError e) {
            // 1.8.8: setOwningPlayer (1.12.1+) is absent; fall back to the name-based owner.
            // the name can be null for an uncached uuid-only player on 1.8.8 - skip in that
            // case (there is no uuid-based skull api there, so the head stays owner-less)
            final String name = player.getName();
            if (name != null) {
                headMeta.setOwner(name);
            }
        }

        head.setItemMeta(headMeta);
        return head;
    }
}
