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
package tech.guilhermekaua.spigotboot.inventoryapi.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PlaceholderApplier} that delegates to PlaceholderAPI when the plugin is enabled at
 * runtime. The class is loaded only when {@code me.clip.placeholderapi.PlaceholderAPI} is on
 * the classpath (the auto-configuration's factory method is guarded by
 * {@code @ConditionalOnClass}).
 *
 * <p>If the PlaceholderAPI plugin happens to be on the classpath but disabled at runtime
 * (rare), {@link #apply(Player, String)} returns the input unchanged.
 */
public class PapiPlaceholderApplier implements PlaceholderApplier {
    private static final String PLUGIN_NAME = "PlaceholderAPI";

    @Override
    public String apply(@Nullable Player player, @Nullable String text) {
        if (text == null) {
            return null;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            return text;
        }

        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
