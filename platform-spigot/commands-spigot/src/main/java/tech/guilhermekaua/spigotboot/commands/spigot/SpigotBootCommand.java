package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Collections;
import java.util.List;

public class SpigotBootCommand extends Command {
    private final Context context;
    private final BootPlugin plugin;
    private final CompiledRootCommand rootCommand;
    private final CommandDispatcher dispatcher;

    public SpigotBootCommand(Context context,
                             CompiledRootCommand rootCommand,
                             CommandDispatcher dispatcher) {
        super(rootCommand.getAliases().getPrimary(), rootCommand.getDescription(), rootCommand.getUsage(), rootCommand.getAliases().getAliases());
        this.context = context;
        this.plugin = context.getPlugin();
        this.rootCommand = rootCommand;
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return dispatcher.dispatch(context, rootCommand, sender, commandLabel, args);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             @NotNull String[] args) throws IllegalArgumentException {
        List<String> completions = dispatcher.complete(context, rootCommand, sender, alias, args);
        return completions == null ? Collections.emptyList() : completions;
    }

    public boolean isOwnedBy(BootPlugin bootPlugin) {
        return this.plugin.equals(bootPlugin);
    }

    public CompiledRootCommand getRootCommand() {
        return rootCommand;
    }

    @Override
    public @Nullable String getPermission() {
        return null;
    }
}
