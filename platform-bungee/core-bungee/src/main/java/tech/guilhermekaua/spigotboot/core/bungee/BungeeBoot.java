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
package tech.guilhermekaua.spigotboot.core.bungee;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Objects;

/**
 * BungeeCord-facing entry point for booting Spigot Boot. A thin facade over the platform-neutral
 * {@code SpigotBoot} bootstrap so Bungee plugins never import a class named "Spigot".
 */
public final class BungeeBoot {

    private BungeeBoot() {
    }

    /**
     * Boots the Spigot Boot context for the given plugin, auto-discovering its modules and components.
     * Delegates to {@link SpigotBoot#initialize(BootPlugin)}.
     *
     * @param plugin the Bungee plugin adapter to boot the context for.
     * @return the initialized {@link Context}.
     * @throws IllegalStateException if a context is already initialized for the plugin.
     */
    public static Context initialize(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        return SpigotBoot.initialize(plugin);
    }

    /**
     * Returns the context previously created for the given plugin, if any.
     * Delegates to {@link SpigotBoot#getContext(BootPlugin)}.
     *
     * @param plugin the Bungee plugin adapter whose context to look up.
     * @return the plugin's {@link Context}, or {@code null} if no context has been initialized for it.
     */
    public static @Nullable Context getContext(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        return SpigotBoot.getContext(plugin);
    }

    /**
     * Destroys the plugin's context if it exists and is initialized; a no-op otherwise.
     * Delegates to {@link SpigotBoot#onDisable(BootPlugin)}.
     *
     * @param plugin the Bungee plugin adapter whose context to tear down.
     */
    public static void onDisable(@NotNull BootPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        SpigotBoot.onDisable(plugin);
    }
}
