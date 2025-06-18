package me.approximations.spigotboot.core.utils;

import lombok.val;
import me.approximations.spigotboot.core.ApxPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

/**
 * @author Yuhtin
 * Github: https://github.com/Yuhtin
 */
public final class TypeUtil {

    public static Material swapLegacy(String material, String legacy) {
        try {
            return Material.valueOf(material);
        } catch (Exception exception) {
            return Material.valueOf(legacy);
        }
    }

    public static ItemStack convertFromLegacy(String materialName, int damage) {
        if (materialName == null || materialName.equalsIgnoreCase("")) return null;

        try {
            final val material = Material.valueOf("LEGACY_" + materialName);
            return new ItemStack(Bukkit.getUnsafe().fromLegacy(new MaterialData(material, (byte) damage)));
        } catch (Exception error) {
            try {
                return new ItemStack(Material.getMaterial(materialName), 1, (short) damage);
            } catch (Exception exception) {
                ApxPlugin.getInstance().getLogger().warning("Material " + materialName + " is invalid!");
                return null;
            }
        }
    }

    public static Material getMaterialFromLegacy(String materialName) {
        if (materialName == null || materialName.equalsIgnoreCase("")) return null;

//        if(materialName.equalsIgnoreCase("SKULL_ITEM")) {
//            try {
//                return Material.PLAYER_HEAD;
//            } catch (Exception error) {
//                try {
//                    return Material.getMaterial(materialName);
//                } catch (Exception exception) {
//                    Main.getInstance().getLogger().warning("Material " + materialName + " is invalid!");
//                    return null;
//                }
//            }
//        }

        try {
            return Material.valueOf("LEGACY_" + materialName);
        } catch (Exception error) {
            try {
                return Material.getMaterial(materialName);
            } catch (Exception exception) {
                ApxPlugin.getInstance().getLogger().warning("Material " + materialName + " is invalid!");
                return null;
            }
        }
    }

}