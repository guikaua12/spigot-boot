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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandsContextReadyRegistrarTest {

    @Test
    void compilesRegistersAndUnregistersOnShutdown() {
        CommandRootCompiler compiler = mock(CommandRootCompiler.class);
        BungeeCommandRegistrar registrar = mock(BungeeCommandRegistrar.class);
        CommandReplacementRegistry replacementRegistry = mock(CommandReplacementRegistry.class);

        Context context = mock(Context.class);
        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getName()).thenReturn("TestPlugin");
        when(context.getPlugin()).thenReturn(bootPlugin);

        CompiledRootCommand root = mock(CompiledRootCommand.class);
        List<CompiledRootCommand> roots = Collections.singletonList(root);
        when(compiler.compile(context)).thenReturn(roots);
        RegisteredCommandSet set = new RegisteredCommandSet(Collections.emptyList());
        when(registrar.register(context, roots)).thenReturn(set);

        new BungeeCommandsContextReadyRegistrar(compiler, registrar, replacementRegistry).onContextReady(context);

        verify(replacementRegistry).register("plugin.name", "TestPlugin");
        verify(replacementRegistry).register("plugin", "TestPlugin");
        verify(registrar).register(context, roots);

        ArgumentCaptor<Runnable> hook = ArgumentCaptor.forClass(Runnable.class);
        verify(context).registerShutdownHook(hook.capture());
        hook.getValue().run();
        verify(registrar).unregister(context, set);
    }

    @Test
    void registersNothingWhenNoCommands() {
        CommandRootCompiler compiler = mock(CommandRootCompiler.class);
        BungeeCommandRegistrar registrar = mock(BungeeCommandRegistrar.class);
        CommandReplacementRegistry replacementRegistry = mock(CommandReplacementRegistry.class);

        Context context = mock(Context.class);
        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getName()).thenReturn("TestPlugin");
        when(context.getPlugin()).thenReturn(bootPlugin);
        when(compiler.compile(context)).thenReturn(Collections.emptyList());

        new BungeeCommandsContextReadyRegistrar(compiler, registrar, replacementRegistry).onContextReady(context);

        verify(registrar, never()).register(any(), any());
        verify(context, never()).registerShutdownHook(any());
    }
}
