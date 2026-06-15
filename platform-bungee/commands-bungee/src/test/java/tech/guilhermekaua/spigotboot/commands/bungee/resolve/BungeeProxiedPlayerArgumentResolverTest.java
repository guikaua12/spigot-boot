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
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeProxiedPlayerArgumentResolverTest {
    private final Plugin plugin = mock(Plugin.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final BungeeProxiedPlayerArgumentResolver resolver = new BungeeProxiedPlayerArgumentResolver(plugin);
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "target", "target", ProxiedPlayer.class, ProxiedPlayer.class, false, false, false, null, null);

    @BeforeEach
    void setUp() {
        when(plugin.getProxy()).thenReturn(proxy);
    }

    @Test
    void supportsOnlyProxiedPlayer() {
        assertTrue(resolver.supports(parameter));
        CommandParameterMetadata stringParam = new CommandParameterMetadata(
                null, 0, "s", "s", String.class, String.class, false, false, false, null, null);
        assertFalse(resolver.supports(stringParam));
    }

    @Test
    void resolveReturnsOnlinePlayer() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(proxy.getPlayer("Alex")).thenReturn(player);
        assertSame(player, resolver.resolve(context, parameter, "Alex"));
    }

    @Test
    void resolveRejectsUnknownPlayer() {
        when(proxy.getPlayer("Ghost")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(context, parameter, "Ghost"));
    }

    @Test
    void defaultCompletionProviderListsOnlinePlayers() {
        ProxiedPlayer a = mock(ProxiedPlayer.class);
        ProxiedPlayer b = mock(ProxiedPlayer.class);
        when(a.getName()).thenReturn("Alex");
        when(b.getName()).thenReturn("Bob");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(a, b));

        List<String> names = resolver.defaultCompletionProvider().complete(context, parameter, "");
        assertEquals(Arrays.asList("Alex", "Bob"), names);
    }
}
