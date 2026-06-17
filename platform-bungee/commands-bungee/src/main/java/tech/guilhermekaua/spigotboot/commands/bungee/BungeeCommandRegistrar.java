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

import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers compiled commands with BungeeCord through its public {@link PluginManager} API. Unlike
 * the Spigot registrar there is no CommandMap reflection: {@code registerCommand}, {@code
 * unregisterCommand} and {@code getCommands} are all public. The public {@code getCommands} view
 * lets this registrar mirror the Spigot collision policy without reflection: a label already owned by
 * this plugin's own {@link BungeeBootCommand} is unregistered and replaced (so reloads work), while a
 * label held by any foreign command raises {@link IllegalStateException} instead of silently shadowing
 * it (BungeeCord's own {@code registerCommand} is otherwise last-wins).
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
        Map<String, Command> knownCommands = indexRegisteredCommands(pluginManager);

        Set<Command> toReplace = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BungeeBootCommand> commands = new ArrayList<>();
        for (CompiledRootCommand root : roots) {
            collectCollisions(plugin, root, knownCommands, toReplace);
            commands.add(new BungeeBootCommand(context, root, dispatcher, commandPlatformSupport));
        }

        for (Command existing : toReplace) {
            pluginManager.unregisterCommand(existing);
        }

        for (BungeeBootCommand command : commands) {
            pluginManager.registerCommand(plugin, command);
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

    private Map<String, Command> indexRegisteredCommands(PluginManager pluginManager) {
        Map<String, Command> knownCommands = new HashMap<>();
        for (Map.Entry<String, Command> entry : pluginManager.getCommands()) {
            knownCommands.put(CommandSupport.normalizeLabel(entry.getKey()), entry.getValue());
        }
        return knownCommands;
    }

    private void collectCollisions(Plugin plugin,
                                   CompiledRootCommand root,
                                   Map<String, Command> knownCommands,
                                   Set<Command> toReplace) {
        for (String label : root.getAliases().allValues()) {
            Command existing = knownCommands.get(CommandSupport.normalizeLabel(label));
            if (existing == null) {
                continue;
            }

            if (existing instanceof BungeeBootCommand && ((BungeeBootCommand) existing).isOwnedBy(plugin)) {
                toReplace.add(existing);
                continue;
            }

            throw new IllegalStateException("Command label collision detected for '" + label + "'.");
        }
    }
}
