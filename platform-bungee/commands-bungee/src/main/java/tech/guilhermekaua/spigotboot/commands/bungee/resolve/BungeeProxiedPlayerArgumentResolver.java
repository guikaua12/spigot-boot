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

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a {@code ProxiedPlayer} command argument by online name, with online-player tab
 * completion. The Bungee analogue of {@code BukkitPlayerArgumentResolver}; like it, an exact lookup is
 * tried first and, failing that, a unique case-insensitive prefix match is accepted (ambiguous
 * prefixes are rejected). Reaches the proxy via the injected {@link Plugin}. Prefix filtering of
 * completions is applied downstream by the framework's {@code CompletionResolver}.
 */
public class BungeeProxiedPlayerArgumentResolver implements CommandArgumentResolver<ProxiedPlayer>, Ordered {
    private final Plugin plugin;

    public BungeeProxiedPlayerArgumentResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return ProxiedPlayer.class.equals(parameter.getValueType());
    }

    @Override
    public ProxiedPlayer resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(input);
        if (player == null) {
            // mirror BukkitPlayerArgumentResolver: fall back to a unique case-insensitive prefix match
            String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
            List<ProxiedPlayer> matches = new ArrayList<>();
            for (ProxiedPlayer candidate : plugin.getProxy().getPlayers()) {
                if (candidate.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1) {
                player = matches.get(0);
            } else if (matches.size() > 1) {
                throw new IllegalArgumentException("Ambiguous player name: " + input);
            }
        }
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + input);
        }
        return player;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                values.add(player.getName());
            }
            return values;
        };
    }
}
