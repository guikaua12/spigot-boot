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

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;

import java.util.List;

/**
 * Compiles every command handler bean and registers the results with BungeeCord when the context is
 * ready, unregistering them on shutdown. Mirrors the Spigot {@code CommandsContextReadyRegistrar}.
 */
public class BungeeCommandsContextReadyRegistrar implements ContextReadyListener, Ordered {
    private final CommandRootCompiler commandRootCompiler;
    private final BungeeCommandRegistrar bungeeCommandRegistrar;
    private final CommandReplacementRegistry replacementRegistry;

    public BungeeCommandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                               BungeeCommandRegistrar bungeeCommandRegistrar,
                                               CommandReplacementRegistry replacementRegistry) {
        this.commandRootCompiler = commandRootCompiler;
        this.bungeeCommandRegistrar = bungeeCommandRegistrar;
        this.replacementRegistry = replacementRegistry;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public void onContextReady(Context context) {
        replacementRegistry.register("plugin.name", context.getPlugin().getName());
        replacementRegistry.register("plugin", context.getPlugin().getName());

        List<CompiledRootCommand> roots = commandRootCompiler.compile(context);
        if (roots.isEmpty()) {
            return;
        }

        final RegisteredCommandSet registered = bungeeCommandRegistrar.register(context, roots);
        context.registerShutdownHook(() -> bungeeCommandRegistrar.unregister(context, registered));
    }
}
