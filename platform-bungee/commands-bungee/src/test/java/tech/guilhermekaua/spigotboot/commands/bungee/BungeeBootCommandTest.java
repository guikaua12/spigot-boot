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

import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeBootCommandTest {

    private CompiledRootCommand rootNamed(String primary) {
        CommandAliasSet aliases = mock(CommandAliasSet.class);
        when(aliases.getPrimary()).thenReturn(primary);
        when(aliases.getAliases()).thenReturn(Collections.emptyList());
        CompiledRootCommand root = mock(CompiledRootCommand.class);
        when(root.getAliases()).thenReturn(aliases);
        return root;
    }

    @Test
    void baseCommandExposesNoPermissionSoDispatcherOwnsIt() {
        BungeeBootCommand command = new BungeeBootCommand(
                mock(Context.class), rootNamed("server"), mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        assertNull(command.getPermission());
    }

    @Test
    void executeDelegatesToDispatcher() {
        Context context = mock(Context.class);
        CompiledRootCommand root = rootNamed("server");
        CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        CommandPlatformSupport support = mock(CommandPlatformSupport.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        CommandSenderHandle handle = mock(CommandSenderHandle.class);
        when(support.createSender(sender)).thenReturn(handle);

        BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, support);
        String[] args = {"send", "Target"};
        command.execute(sender, args);

        verify(dispatcher).dispatch(context, root, handle, "server", args);
    }

    @Test
    void onTabCompleteDelegatesToDispatcherAndIsNullSafe() {
        Context context = mock(Context.class);
        CompiledRootCommand root = rootNamed("server");
        CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        CommandPlatformSupport support = mock(CommandPlatformSupport.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        CommandSenderHandle handle = mock(CommandSenderHandle.class);
        when(support.createSender(sender)).thenReturn(handle);
        when(dispatcher.complete(context, root, handle, "server", new String[]{"send", "T"}))
                .thenReturn(Collections.singletonList("Target"));

        BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, support);

        Iterable<String> result = command.onTabComplete(sender, new String[]{"send", "T"});
        List<String> values = new ArrayList<>();
        result.forEach(values::add);
        assertEquals(Collections.singletonList("Target"), values);

        when(dispatcher.complete(context, root, handle, "server", new String[]{"x"})).thenReturn(null);
        assertFalse(command.onTabComplete(sender, new String[]{"x"}).iterator().hasNext());
    }
}
