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

import io.github.bananapuncher714.nbteditor.NBTEditor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A utility class that helps to create and modify ItemStacks.
 */
public class ItemBuilder {

    private ItemStack item;

    /**
     * Creates a new ItemBuilder with the given ItemStack.
     *
     * @param item the ItemStack to use
     */
    public ItemBuilder(ItemStack item) {
        this.item = item;
    }

    /**
     * Creates a new ItemBuilder with the given Material type.
     *
     * @param type the Material type to use
     */
    public ItemBuilder(Material type) {
        this(new ItemStack(type));
    }

    /**
     * Creates a new ItemBuilder with the given Material type and data value.
     *
     * @param type the Material type to use
     * @param data the data value to use
     */
    public ItemBuilder(Material type, int data) {
        this(new ItemStack(type, 1, (short) data));
    }

    /**
     * Creates a new ItemBuilder with a skull item from the given URL.
     *
     * @param url the URL to get the skull item from
     */
    public ItemBuilder(String url) {
        item = NBTEditor.getHead(url);
    }

    /**
     * Creates a new ItemBuilder with a leather armor item of the given color.
     *
     * @param type  the Material type to use
     * @param color the Color to use
     */
    public ItemBuilder(Material type, Color color) {
        item = new ItemStack(type);

        final LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
    }

    public ItemBuilder setItem(ItemStack item) {
        this.item = item;
        return this;
    }

    public ItemBuilder setGlow(boolean glow) {
        changeItemMeta(meta -> {
            if (glow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                for (Enchantment enchantment : meta.getEnchants().keySet()) {
                    meta.removeEnchant(enchantment);
                }
            }
        });

        return this;
    }

    /**
     * Changes the ItemMeta of the ItemStack using the given Consumer function.
     *
     * @param consumer the Consumer function to apply to the ItemMeta
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder changeItemMeta(Consumer<ItemMeta> consumer) {
        final ItemMeta itemMeta = item.getItemMeta();
        consumer.accept(itemMeta);
        item.setItemMeta(itemMeta);
        return this;
    }

    /**
     * Sets the display name of the ItemStack.
     *
     * @param name the display name to use
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setName(String name) {
        return changeItemMeta(it -> it.setDisplayName(ColorUtil.colored(name)));
    }

    /**
     * Adds some strings to the lore of the ItemStack.
     *
     * @param lore an array of strings to add to the lore
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder addLore(String... lore) {
        return changeItemMeta(it -> {
            List<String> l = it.getLore();
            if (l == null) {
                l = new ArrayList<>();
            }
            l.addAll(ColorUtil.colored(Arrays.asList(lore)));
            it.setLore(l);
        });
    }

    /**
     * Adds a string to the lore of the ItemStack at the given index.
     *
     * @param index the index to insert the string at
     * @param lore  the string to add to the lore
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder addLore(int index, String lore) {
        return changeItemMeta(it -> {
            List<String> l = it.getLore();
            if (l == null) {
                l = ColorUtil.colored(Collections.singletonList(lore));
                return;
            }
            l.add(index, ColorUtil.colored(lore));
            it.setLore(l);
        });
    }

    /**
     * Replaces a string in the lore of the ItemStack at the given index.
     *
     * @param index the index to replace the string at
     * @param lore  the string to replace in the lore
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setLore(int index, String lore) {
        return changeItemMeta(it -> {
            List<String> l = it.getLore();
            if (l == null) {
                l = ColorUtil.colored(Collections.singletonList(lore));
                return;
            }
            l.set(index, ColorUtil.colored(lore));
            it.setLore(l);
        });
    }

    /**
     * Deletes a string from the lore of the ItemStack at the given index.
     *
     * @param index the index to delete the string from
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder deleteLoreLine(int index) {
        return changeItemMeta(it -> {
            final List<String> l = it.getLore();
            if (l == null) {
                return;
            }
            l.remove(index);
            it.setLore(l);
        });
    }

    /**
     * Finds the index of a string in the lore of the ItemStack that contains the given substring.
     *
     * @param lore the substring to look for in the lore
     * @return the index of the string that contains the substring, or -1 if not found
     */
    public int findLore(String lore) {
        final List<String> l = item.getItemMeta().getLore();
        if (l == null) {
            return -1;
        }

        return l.stream().filter(s -> s.contains(lore)).map(l::indexOf).findFirst().orElse(-1);
    }

    /**
     * Returns the lore of the ItemStack as a list of strings.
     *
     * @return the lore of the ItemStack, or null if none
     */
    public List<String> getLore() {
        return item.getItemMeta().getLore();
    }

    /**
     * Sets the lore of the ItemStack.
     *
     * @param lore an array of strings to use as the lore
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setLore(String... lore) {
        return changeItemMeta(it -> it.setLore(ColorUtil.colored(Arrays.asList(lore))));
    }

    /**
     * Sets the lore of the ItemStack.
     *
     * @param lore a list of strings to use as the lore
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setLore(List<String> lore) {
        return changeItemMeta(it -> it.setLore(ColorUtil.colored(lore)));
    }

    /**
     * Replaces a placeholder in the lore of the ItemStack with a given value.
     *
     * @param placeholder the placeholder to replace
     * @param value       the value to replace with
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder placeholder(String placeholder, String value) {
        return changeItemMeta(itemMeta -> {
            List<String> lore = itemMeta.getLore();

            if (lore == null) lore = new ArrayList<>();

            final List<String> newLore = lore.stream().map(l -> l.replace(placeholder, value)).collect(Collectors.toList());

            itemMeta.setLore(newLore);

            final String displayName = itemMeta.getDisplayName();

            if (displayName == null) return;

            itemMeta.setDisplayName(displayName.replace(placeholder, value));
        });
    }

    /**
     * Sets the stack size, clamped to at least 1.
     *
     * @param amount the desired amount
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setAmount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    /**
     * Sets the legacy durability/data value used by old pre-flattening item variants.
     *
     * @param data the legacy data value, or null to leave it unchanged
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setLegacyModelData(Integer data) {
        ModelDataCompat.setLegacyData(item, data);
        return this;
    }

    /**
     * Sets the custom model data. This uses the component-backed custom model data float list when
     * available on newer servers, falls back to the 1.14+ integer API, and is a no-op on older
     * servers or when {@code data} is null.
     *
     * @param data the custom model data, or null to leave it unset
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelData(Integer data) {
        if (data == null) {
            return this;
        }
        return changeItemMeta(meta -> ModelDataCompat.setCustomModelData(meta, data));
    }

    /**
     * Sets custom model data component values on servers that expose that API. Null lists are left
     * unchanged on the component snapshot.
     *
     * @param floats  range dispatch float values, or null to leave unchanged
     * @param flags   condition flag values, or null to leave unchanged
     * @param strings select string values, or null to leave unchanged
     * @param colors  tint colors, or null to leave unchanged
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelDataComponent(
            List<Float> floats,
            List<Boolean> flags,
            List<String> strings,
            List<Color> colors
    ) {
        return changeItemMeta(meta -> ModelDataCompat.setCustomModelDataComponent(meta, floats, flags, strings, colors));
    }

    /**
     * Sets custom model data component float values.
     *
     * @param floats range dispatch float values
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelDataFloats(Float... floats) {
        return setCustomModelDataComponent(asListOrNull(floats), null, null, null);
    }

    /**
     * Sets custom model data component boolean flag values.
     *
     * @param flags condition flag values
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelDataFlags(Boolean... flags) {
        return setCustomModelDataComponent(null, asListOrNull(flags), null, null);
    }

    /**
     * Sets custom model data component string values.
     *
     * @param strings select string values
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelDataStrings(String... strings) {
        return setCustomModelDataComponent(null, null, asListOrNull(strings), null);
    }

    /**
     * Sets custom model data component tint colors.
     *
     * @param colors tint colors
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setCustomModelDataColors(Color... colors) {
        return setCustomModelDataComponent(null, null, null, asListOrNull(colors));
    }

    /**
     * Sets the direct item model key on servers that expose that API.
     *
     * @param itemModel a namespaced key such as {@code my_pack:bronze_sword}
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setItemModel(String itemModel) {
        if (itemModel == null) {
            return this;
        }
        return changeItemMeta(meta -> ModelDataCompat.setItemModel(meta, itemModel));
    }

    /**
     * Resolves the first {@link Material} that matches one of the given config names (modern name
     * first, legacy fallback via {@link TypeUtil#getMaterialFromLegacy(String)}), falling back to
     * {@link Material#STONE} when none resolve — so a GUI never fails to render an item.
     *
     * @param candidates material names to try in order
     * @return a builder for the resolved material, or STONE
     */
    public static ItemBuilder ofMaterial(String... candidates) {
        if (candidates != null) {
            for (String name : candidates) {
                Material material = TypeUtil.getMaterialFromLegacy(name);
                if (material != null) {
                    return new ItemBuilder(material);
                }
            }
        }
        return new ItemBuilder(Material.STONE);
    }

    /**
     * Sets the display name verbatim, without colour translation — for names that are already
     * formatted (e.g. by {@code ChatMarkup.legacy(...)}).
     *
     * @param name the pre-formatted display name
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setRawName(String name) {
        return changeItemMeta(it -> it.setDisplayName(name));
    }

    /**
     * Sets the lore verbatim, without colour translation — for lines that are already formatted.
     *
     * @param lore the pre-formatted lore lines
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setRawLore(List<String> lore) {
        return changeItemMeta(it -> it.setLore(lore));
    }

    /**
     * Sets the lore verbatim, without colour translation — for lines that are already formatted.
     *
     * @param lore the pre-formatted lore lines
     * @return this ItemBuilder instance for chaining
     */
    public ItemBuilder setRawLore(String... lore) {
        return setRawLore(Arrays.asList(lore));
    }

    /**
     * Returns the ItemStack that this builder is working on.
     *
     * @return the ItemStack instance
     */
    public ItemStack wrap() {
        return item;
    }

    private static <T> List<T> asListOrNull(T[] values) {
        return values == null ? null : Arrays.asList(values);
    }

}
