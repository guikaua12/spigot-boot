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
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Completion;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Permission;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.ConnectionService;

import java.util.Optional;

/**
 * {@code /server} command tree: move players between servers, find where a player is, and list
 * configured servers. Argument types {@link ProxiedPlayer} and {@link ServerInfo} are resolved by
 * the Bungee argument resolvers; the {@code onlinePlayers}/{@code servers} completions are provided
 * by the Bungee completion customizer.
 */
@CommandHandler
@RootCommand(value = "server", aliases = "srv")
@RequiredArgsConstructor
public class ServerCommands {
    private final ConnectionService connections;
    private final MessagesConfig messages;
    private final Plugin plugin;

    @DefaultCommand
    public void info(@Sender CommandSender sender) {
        reply(sender, "&7Usage: &b/server send <player> <server>&7, &b/server find <player>&7, &b/server list");
    }

    @Command("send <target> <server>")
    @Permission("network.server.send")
    public void send(@Sender CommandSender sender,
                     @Completion("onlinePlayers") ProxiedPlayer target,
                     @Completion("servers") ServerInfo server) {
        connections.send(target, server);
        reply(sender, "&aSent &b" + target.getName() + " &ato &b" + server.getName() + "&a.");
    }

    @Command("find <target>")
    public void find(@Sender CommandSender sender,
                     @Completion("onlinePlayers") ProxiedPlayer target) {
        Optional<ServerInfo> server = connections.locate(target.getName());
        if (server.isPresent()) {
            reply(sender, "&b" + target.getName() + " &7is on &b" + server.get().getName() + "&7.");
        } else {
            reply(sender, "&c" + target.getName() + " is not connected to any server.");
        }
    }

    @Command("list")
    public void list(@Sender CommandSender sender) {
        String servers = String.join("&7, &b", plugin.getProxy().getServers().keySet());
        reply(sender, "&7Servers: &b" + servers);
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message)));
    }
}
