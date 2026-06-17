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
package tech.guilhermekaua.spigotboot.commands.bungee.resolve;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;

/**
 * Resolves a {@code ServerInfo} command argument by configured server name, with server-name tab
 * completion. A proxy-specific resolver with no Bukkit analogue; reaches the proxy via the injected
 * {@link Plugin}. Prefix filtering of completions is applied downstream by {@code CompletionResolver}.
 */
public class BungeeServerArgumentResolver implements CommandArgumentResolver<ServerInfo>, Ordered {
    private final Plugin plugin;

    public BungeeServerArgumentResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return ServerInfo.class.equals(parameter.getValueType());
    }

    @Override
    public ServerInfo resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        ServerInfo server = plugin.getProxy().getServerInfo(input);
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + input);
        }
        return server;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> new ArrayList<>(plugin.getProxy().getServers().keySet());
    }
}
