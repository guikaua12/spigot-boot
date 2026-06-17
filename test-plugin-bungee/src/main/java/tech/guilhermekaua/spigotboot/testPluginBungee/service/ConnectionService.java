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
package tech.guilhermekaua.spigotboot.testPluginBungee.service;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;

import java.util.Optional;

/**
 * Encapsulates proxy connection actions: sending players to servers and locating where a player is.
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {
    private final Plugin plugin;
    private final NetworkConfig config;

    /**
     * connects a player to the given server.
     *
     * @param player the player to move.
     * @param server the destination server.
     */
    public void send(@NotNull ProxiedPlayer player, @NotNull ServerInfo server) {
        player.connect(server);
    }

    /**
     * locates the server an online player is currently connected to.
     *
     * @param playerName the player name to look up.
     * @return the player's current server, or empty if the player is offline or not on a server.
     */
    public Optional<ServerInfo> locate(@NotNull String playerName) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null || player.getServer() == null) {
            return Optional.empty();
        }
        return Optional.of(player.getServer().getInfo());
    }

    /**
     * resolves the configured default ("lobby") server.
     *
     * @return the default {@link ServerInfo}, or {@code null} if it is not configured on the proxy.
     */
    public ServerInfo defaultServer() {
        return plugin.getProxy().getServerInfo(config.getDefaultServer());
    }
}
