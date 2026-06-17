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
package tech.guilhermekaua.spigotboot.testPluginBungee.command;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.ConnectionService;

/**
 * {@code /lobby} command: sends the caller to the configured default server. A config-driven
 * command that injects only the collaborators it needs.
 */
@CommandHandler
@RootCommand("lobby")
@RequiredArgsConstructor
public class LobbyCommand {
    private final ConnectionService connections;
    private final MessagesConfig messages;

    @DefaultCommand
    public void lobby(@Sender ProxiedPlayer sender) {
        ServerInfo lobby = connections.defaultServer();
        if (lobby == null) {
            send(sender, "&cThe lobby server is not configured.");
            return;
        }
        connections.send(sender, lobby);
        send(sender, "&aSending you to the lobby...");
    }

    private void send(ProxiedPlayer player, String message) {
        player.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message)));
    }
}
