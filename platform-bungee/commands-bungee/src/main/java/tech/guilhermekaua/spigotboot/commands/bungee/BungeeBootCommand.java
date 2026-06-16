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

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.Collections;
import java.util.List;

/**
 * A BungeeCord {@link Command} that bridges a compiled root command to the generic
 * {@link CommandDispatcher}. Mirrors the Spigot {@code SpigotBootCommand}.
 *
 * <p>The base command is constructed with a {@code null} permission: BungeeCord's
 * {@code PluginManager} only blocks execution when {@code hasPermission} fails, so leaving it null
 * guarantees {@link #execute}/{@link #onTabComplete} are always reached and the dispatcher enforces
 * per-route {@code @Permission} for both — the same end-state as the Spigot adapter.
 *
 * <p>{@link TabExecutor} is implemented because BungeeCord's base {@link Command} has no tab-complete
 * method; the dispatcher invokes {@link #onTabComplete} via {@code instanceof TabExecutor}.
 */
public class BungeeBootCommand extends Command implements TabExecutor {
    private final Context context;
    private final Plugin plugin;
    private final CompiledRootCommand rootCommand;
    private final CommandDispatcher dispatcher;
    private final CommandPlatformSupport commandPlatformSupport;

    public BungeeBootCommand(Context context,
                             CompiledRootCommand rootCommand,
                             CommandDispatcher dispatcher,
                             CommandPlatformSupport commandPlatformSupport) {
        super(rootCommand.getAliases().getPrimary(), null,
                rootCommand.getAliases().getAliases().toArray(new String[0]));
        this.context = context;
        this.plugin = context.getBean(Plugin.class);
        this.rootCommand = rootCommand;
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // BungeeCord's execute() is void and provides no label; the primary name is used as the label.
        dispatcher.dispatch(context, rootCommand, commandPlatformSupport.createSender(sender), getName(), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = dispatcher.complete(context, rootCommand,
                commandPlatformSupport.createSender(sender), getName(), args);
        return completions == null ? Collections.emptyList() : completions;
    }

    public CompiledRootCommand getRootCommand() {
        return rootCommand;
    }

    /**
     * Returns whether this command was registered by the given plugin. Mirrors the Spigot
     * {@code SpigotBootCommand#isOwnedBy}; {@link BungeeCommandRegistrar} uses it to tell a
     * re-registration of this plugin's own command apart from a genuine foreign collision.
     *
     * @param plugin the plugin to test ownership against
     * @return {@code true} if this command belongs to {@code plugin}
     */
    public boolean isOwnedBy(Plugin plugin) {
        return this.plugin != null && this.plugin.equals(plugin);
    }
}
