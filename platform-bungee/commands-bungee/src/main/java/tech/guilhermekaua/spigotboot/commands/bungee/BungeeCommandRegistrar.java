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

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers compiled commands with BungeeCord through its public {@link PluginManager} API. Unlike
 * the Spigot registrar there is no CommandMap reflection: {@code registerCommand}/{@code
 * unregisterCommand} are public. BungeeCord's command map is private and last-wins, so no collision
 * detection is performed (see the design spec).
 */
public class BungeeCommandRegistrar {
    private final CommandDispatcher dispatcher;
    private final CommandPlatformSupport commandPlatformSupport;

    public BungeeCommandRegistrar(CommandDispatcher dispatcher,
                                  CommandPlatformSupport commandPlatformSupport) {
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    public RegisteredCommandSet register(Context context, List<CompiledRootCommand> roots) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();

        List<BungeeBootCommand> commands = new ArrayList<>();
        for (CompiledRootCommand root : roots) {
            BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, commandPlatformSupport);
            pluginManager.registerCommand(plugin, command);
            commands.add(command);
        }
        return new RegisteredCommandSet(commands);
    }

    public void unregister(Context context, RegisteredCommandSet registeredCommandSet) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();
        for (BungeeBootCommand command : registeredCommandSet.getCommands()) {
            pluginManager.unregisterCommand(command);
        }
    }
}
