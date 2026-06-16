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
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandRegistrarTest {

    private CompiledRootCommand rootNamed(String primary) {
        CommandAliasSet aliases = mock(CommandAliasSet.class);
        when(aliases.getPrimary()).thenReturn(primary);
        when(aliases.getAliases()).thenReturn(Collections.emptyList());
        when(aliases.allValues()).thenReturn(Collections.singletonList(primary));
        CompiledRootCommand root = mock(CompiledRootCommand.class);
        when(root.getAliases()).thenReturn(aliases);
        return root;
    }

    @Test
    void registersEachRootAndUnregistersThem() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        when(pluginManager.getCommands()).thenReturn(Collections.<Map.Entry<String, Command>>emptyList());

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);

        BungeeCommandRegistrar registrar = new BungeeCommandRegistrar(
                mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        RegisteredCommandSet set = registrar.register(
                context, Arrays.asList(rootNamed("server"), rootNamed("network")));

        assertEquals(2, set.getCommands().size());
        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(pluginManager, times(2)).registerCommand(eq(plugin), captor.capture());
        List<String> names = captor.getAllValues().stream().map(Command::getName).toList();
        assertTrue(names.contains("server"));
        assertTrue(names.contains("network"));

        registrar.unregister(context, set);
        verify(pluginManager).unregisterCommand(set.getCommands().get(0));
        verify(pluginManager).unregisterCommand(set.getCommands().get(1));
    }

    @Test
    void rejectsCollisionWithForeignCommand() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        // an unrelated plugin already owns /server (Bungee lower-cases registered labels)
        Command foreign = mock(Command.class);
        Map<String, Command> known = new HashMap<>();
        known.put("server", foreign);
        when(pluginManager.getCommands()).thenReturn(known.entrySet());

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);

        BungeeCommandRegistrar registrar = new BungeeCommandRegistrar(
                mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        assertThrows(IllegalStateException.class,
                () -> registrar.register(context, Collections.singletonList(rootNamed("server"))));
        verify(pluginManager, never()).registerCommand(any(), any());
    }

    @Test
    void replacesOwnCommandOnReRegistration() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);

        // a previously-registered command owned by this same plugin (e.g. a reload)
        BungeeBootCommand previous = new BungeeBootCommand(
                context, rootNamed("server"), mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));
        Map<String, Command> known = new HashMap<>();
        known.put("server", previous);
        when(pluginManager.getCommands()).thenReturn(known.entrySet());

        BungeeCommandRegistrar registrar = new BungeeCommandRegistrar(
                mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        RegisteredCommandSet set = registrar.register(
                context, Collections.singletonList(rootNamed("server")));

        assertEquals(1, set.getCommands().size());
        verify(pluginManager).unregisterCommand(previous);
        verify(pluginManager).registerCommand(eq(plugin), eq(set.getCommands().get(0)));
    }
}
