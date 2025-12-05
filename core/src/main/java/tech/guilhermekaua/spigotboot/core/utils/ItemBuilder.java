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
package tech.guilhermekaua.spigotboot.core.utils;

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
                l = ColorUtil.colored(Arrays.asList(lore));
                return;
            }
            l.addAll(ColorUtil.colored(Arrays.asList(lore)));
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
     * Returns the ItemStack that this builder is working on.
     *
     * @return the ItemStack instance
     */
    public ItemStack wrap() {
        return item;
    }


}