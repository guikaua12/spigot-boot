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
package tech.guilhermekaua.spigotboot.inventoryapi.item.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

/**
 * Builds {@link ItemStack}s from legacy material name + damage pairs, handling the 1.13 flattening
 * by retrying through {@code Bukkit.getUnsafe().fromLegacy(...)} when the modern lookup fails.
 *
 * <p>Returns {@code null} when the material name cannot be resolved on the running server.
 */
public final class MaterialUtil {

    private MaterialUtil() {
    }

    /**
     * @return a one-item stack for the legacy pair, or {@code null} if the name is unknown
     */
    public static ItemStack convertFromLegacy(String materialName, int damage) {
        try {
            Material material = Material.getMaterial(materialName);
            if (material == null) {
                return null;
            }
            return new ItemStack(material, 1, (short) damage);
        } catch (Exception error) {
            try {
                Material material = Material.valueOf("LEGACY_" + materialName);
                return new ItemStack(Bukkit.getUnsafe().fromLegacy(new MaterialData(material, (byte) damage)));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

}
