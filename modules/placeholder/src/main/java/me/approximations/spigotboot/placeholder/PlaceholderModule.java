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
package me.approximations.spigotboot.placeholder;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.ApxPlugin;
import me.approximations.spigotboot.core.module.Module;
import me.approximations.spigotboot.core.module.annotations.ConditionalOnClass;
import me.approximations.spigotboot.placeholder.registry.PlaceholderRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;

@RequiredArgsConstructor
@ConditionalOnClass(value = PlaceholderExpansion.class, message = "PlaceholderAPI not found, skipping registration of placeholders.")
public class PlaceholderModule implements Module {
    private static final String PAPI_NAME = "PlaceholderAPI";

    private final ApxPlugin plugin;
    private final PlaceholderRegistry placeholderRegistry;

    @Override
    public void initialize() throws Exception {
        plugin.getLogger().info("Initializing Placeholder Module...");

        if (!Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
            plugin.getLogger().info("PlaceholderAPI not found, skipping registration of placeholders.");
            return;
        }

        placeholderRegistry.initialize();
        plugin.addDisableEntry(() -> {
            plugin.getLogger().info("Unregistering PlaceholderAPI expansion...");
            if (Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
                placeholderRegistry.unregister();
            }
        });
    }
}
