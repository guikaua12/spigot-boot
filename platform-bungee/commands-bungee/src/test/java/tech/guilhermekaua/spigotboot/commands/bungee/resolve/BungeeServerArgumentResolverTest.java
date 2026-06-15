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

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeServerArgumentResolverTest {
    private final Plugin plugin = mock(Plugin.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final BungeeServerArgumentResolver resolver = new BungeeServerArgumentResolver(plugin);
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "server", "server", ServerInfo.class, ServerInfo.class, false, false, false, null, null);

    @BeforeEach
    void setUp() {
        when(plugin.getProxy()).thenReturn(proxy);
    }

    @Test
    void supportsOnlyServerInfo() {
        assertTrue(resolver.supports(parameter));
    }

    @Test
    void resolveReturnsServer() {
        ServerInfo lobby = mock(ServerInfo.class);
        when(proxy.getServerInfo("lobby")).thenReturn(lobby);
        assertSame(lobby, resolver.resolve(context, parameter, "lobby"));
    }

    @Test
    void resolveRejectsUnknownServer() {
        when(proxy.getServerInfo("void")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(context, parameter, "void"));
    }

    @Test
    void defaultCompletionProviderListsServers() {
        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        servers.put("lobby", mock(ServerInfo.class));
        servers.put("survival", mock(ServerInfo.class));
        when(proxy.getServers()).thenReturn(servers);

        List<String> names = resolver.defaultCompletionProvider().complete(context, parameter, "");
        assertTrue(names.contains("lobby"));
        assertTrue(names.contains("survival"));
    }
}
