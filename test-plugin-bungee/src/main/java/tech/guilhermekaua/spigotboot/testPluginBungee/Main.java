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
package tech.guilhermekaua.spigotboot.testPluginBungee;

import lombok.Getter;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBoot;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBootPlugin;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;

/**
 * Sample BungeeCord plugin main class. Declares the descriptor via {@link BungeePlugin} (which
 * generates {@code bungee.yml}) and boots the Spigot Boot DI container on enable.
 */
@Getter
@BungeePlugin(
        name = "NetworkManager",
        version = "1.0.0",
        author = "Approximations",
        description = "A sample BungeeCord plugin for the Spigot Boot framework."
)
public class Main extends Plugin {
    private BungeeBootPlugin bootPlugin;
    private Context context;

    @Override
    public void onEnable() {
        bootPlugin = new BungeeBootPlugin(this);
        context = BungeeBoot.initialize(bootPlugin);

        NetworkConfig config = context.getBean(NetworkConfig.class);
        getLogger().info("NetworkManager enabled. Default server: " + config.getDefaultServer()
                + ", max network players: " + config.getMaxNetworkPlayers() + ".");
    }

    @Override
    public void onDisable() {
        BungeeBoot.onDisable(bootPlugin);
    }
}
