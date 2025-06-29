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

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Utils {
    public static ItemStack getItemFromConfig(ConfigurationSection section) {
        final String name = section.getString("name");
        final List<String> lore = section.getStringList("lore");
        final boolean useHead = section.getBoolean("head.enable");
        final Material material = TypeUtil.getMaterialFromLegacy(section.getString("material"));
        final int data = section.getInt("data", -1);
        final boolean glow = section.getBoolean("glow", false);


        if (name == null || lore == null || material == null || data == -1) {
            return null;
        }

        if (useHead) {
            final String url = section.getString("head.url");
            return new ItemBuilder(url).setName(name).setLore(lore).setGlow(glow).wrap();
        } else {
            return new ItemBuilder(material, data).setName(name).setLore(lore).setGlow(glow).wrap();
        }
    }

    public static void tryElsePrint(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static <T> void shuffleList(List<T> list) {
        final int size = list.size();

        for (int i = size - 1; i > 0; i--) {
            final int j = ThreadLocalRandom.current().nextInt(i + 1);
            final T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }

    public static <T> void moveLastItemToFirst(List<T> list) {
        if (!list.isEmpty()) {
            final T lastItem = list.get(list.size() - 1);

            // Shift all elements to the right by one position
            for (int i = list.size() - 1; i > 0; i--) {
                list.set(i, list.get(i - 1));
            }

            // Move the last item to the first position
            list.set(0, lastItem);
        }
    }

    public static int getMaxValue(Set<Integer> set) {
        int maxValue = 1;
        for (int value : set) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        return maxValue;
    }

    public static <T> void removeDuplicates(List<T> list) {
        final Set<T> set = new HashSet<>(list);
        list.clear();
        list.addAll(set);
    }

    // sneak throw
    public static <T> T sneakThrow(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable e) {
        }
        return null;
    }

    public static void sneakThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
        }
    }

    public static void sync(Plugin plugin, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTask(plugin, runnable);
    }

    public static void syncLater(Plugin plugin, Runnable runnable, long delay) {
        Bukkit.getServer().getScheduler().runTaskLater(plugin, runnable, delay);
    }

    public static <T> T find(Collection<T> collection, Predicate<T> predicate) {
        for (T element : collection) {
            if (predicate.test(element)) {
                return element;
            }
        }
        return null;
    }

    public static void playSound(Plugin plugin, Player player, String name) {
        Bukkit.getServer().getScheduler().runTask(plugin, () -> {
            Utils.tryElsePrint(() -> {
                final Sound sound = Sound.valueOf(name);
                player.playSound(player.getEyeLocation(), sound, 1.0f, 1.0f);
            });
        });
    }

    public static long millisToTicks(long millis) {
        return millis / 50;
    }
}
