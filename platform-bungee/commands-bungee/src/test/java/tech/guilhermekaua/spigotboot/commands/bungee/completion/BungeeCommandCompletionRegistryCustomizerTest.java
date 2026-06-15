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

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeCommandCompletionRegistryCustomizerTest {

    @Test
    void registersOnlinePlayersAndServersWithPrefixFiltering() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(plugin.getProxy()).thenReturn(proxy);

        ProxiedPlayer alex = mock(ProxiedPlayer.class);
        ProxiedPlayer bob = mock(ProxiedPlayer.class);
        when(alex.getName()).thenReturn("Alex");
        when(bob.getName()).thenReturn("Bob");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(alex, bob));

        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        servers.put("lobby", mock(ServerInfo.class));
        servers.put("survival", mock(ServerInfo.class));
        when(proxy.getServers()).thenReturn(servers);

        Map<String, CommandCompletionProvider> registered = new HashMap<>();
        CommandCompletionRegistry registry = new CommandCompletionRegistry() {
            @Override
            public void register(String id, CommandCompletionProvider provider) {
                registered.put(id, provider);
            }

            @Override
            public Optional<CommandCompletionProvider> resolve(String id) {
                return Optional.ofNullable(registered.get(id));
            }
        };

        new BungeeCommandCompletionRegistryCustomizer(plugin).customize(registry);

        assertTrue(registered.containsKey("onlinePlayers"));
        assertTrue(registered.containsKey("servers"));

        CommandExecutionContext ctx = mock(CommandExecutionContext.class);
        assertEquals(Collections.singletonList("Alex"),
                registered.get("onlinePlayers").complete(ctx, null, "a"));
        assertEquals(Collections.singletonList("lobby"),
                registered.get("servers").complete(ctx, null, "l"));
    }
}
