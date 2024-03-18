package me.approximations.apxPlugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
        headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));

        head.setItemMeta(headMeta);
        return head;
    }
}
