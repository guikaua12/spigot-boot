package tech.guilhermekaua.spigotboot.core.spigot.utils;

import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import java.util.logging.Logger;

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
                Logger.getGlobal().warning("Material " + materialName + " is invalid!");
                return null;
            }
        }
    }

    /**
     * Resolves a {@link Material} from a config name, preferring the modern (non-legacy) material.
     * <p>
     * The modern name is tried first so that names such as {@code BEDROCK} resolve to
     * {@link Material#BEDROCK} instead of {@code LEGACY_BEDROCK} on post-flattening servers (1.13+).
     * Only when no modern material matches is a {@code LEGACY_}-prefixed lookup attempted, so that
     * pre-1.13 names still resolve. On 1.8.8 (no {@code LEGACY_*} constants exist) the first lookup
     * already returns the native material.
     *
     * @param materialName the material name from config, may be null or empty
     * @return the resolved material, or null if the name is null, empty, or unknown
     */
    public static Material getMaterialFromLegacy(String materialName) {
        if (materialName == null || materialName.equalsIgnoreCase("")) return null;

        try {
            return Material.valueOf(materialName);
        } catch (Exception error) {
            try {
                return Material.valueOf("LEGACY_" + materialName);
            } catch (Exception exception) {
                Logger.getGlobal().warning("Material " + materialName + " is invalid!");
                return null;
            }
        }
    }

}
