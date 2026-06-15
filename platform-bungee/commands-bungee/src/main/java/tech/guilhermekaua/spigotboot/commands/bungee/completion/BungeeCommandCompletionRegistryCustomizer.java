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
package tech.guilhermekaua.spigotboot.commands.bungee.completion;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers the proxy-specific named completions {@code onlinePlayers} and {@code servers}, referenced
 * from handlers via {@code @Completion("onlinePlayers")} / {@code @Completion("servers")}. Mirrors the
 * Spigot {@code BukkitCommandCompletionRegistryCustomizer} ({@code worlds}/{@code materials} have no
 * proxy analogue). Reaches the proxy via the injected {@link Plugin}.
 */
public class BungeeCommandCompletionRegistryCustomizer implements CommandCompletionRegistryCustomizer {
    private final Plugin plugin;

    public BungeeCommandCompletionRegistryCustomizer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void customize(CommandCompletionRegistry registry) {
        registry.register("onlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                if (!lowerInput.isEmpty() && !player.getName().toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(player.getName());
            }
            return values;
        });
        registry.register("servers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (String serverName : plugin.getProxy().getServers().keySet()) {
                if (!lowerInput.isEmpty() && !serverName.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(serverName);
            }
            return values;
        });
    }
}
