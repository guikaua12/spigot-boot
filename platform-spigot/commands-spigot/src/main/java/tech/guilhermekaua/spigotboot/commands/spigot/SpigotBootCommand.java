package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
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
    private final CommandPlatformSupport commandPlatformSupport;

    public SpigotBootCommand(Context context,
                             CompiledRootCommand rootCommand,
                             CommandDispatcher dispatcher,
                             CommandPlatformSupport commandPlatformSupport) {
        super(rootCommand.getAliases().getPrimary(), rootCommand.getDescription(), rootCommand.getUsage(), rootCommand.getAliases().getAliases());
        this.context = context;
        this.plugin = context.getPlugin();
        this.rootCommand = rootCommand;
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return dispatcher.dispatch(context, rootCommand, commandPlatformSupport.createSender(sender), commandLabel, args);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             @NotNull String[] args) throws IllegalArgumentException {
        List<String> completions = dispatcher.complete(
                context,
                rootCommand,
                commandPlatformSupport.createSender(sender),
                alias,
                args
        );
        return completions == null ? Collections.emptyList() : completions;
    }

    public boolean isOwnedBy(BootPlugin bootPlugin) {
        return this.plugin.equals(bootPlugin);
    }

    public CompiledRootCommand getRootCommand() {
        return rootCommand;
    }

    /**
     * Returns {@code null} so Bukkit carries no static permission for this command; visibility is computed
     * per-sender in {@link #testPermissionSilent(CommandSender)} instead. Per-route permissions
     * ({@literal @Permission}) are enforced by {@link CommandDispatcher} during both dispatch and
     * tab-completion.
     */
    @Override
    public @Nullable String getPermission() {
        return null;
    }

    /**
     * Reports whether {@code target} can use at least one route of this command. CraftBukkit builds each
     * command's client-side command-tree node with a {@code requires(...)} predicate backed by this method,
     * so returning the route-aware result hides the command in tab completion from senders who can run none
     * of its subcommands (nor its default handler). Execution is unaffected — Bukkit's command dispatch does
     * not consult permissions — so {@link CommandDispatcher} still enforces per-route {@literal @Permission}
     * during {@link #execute} and {@link #tabComplete} for senders who pass this gate.
     *
     * @param target the sender being tested
     * @return {@code true} if {@code target} can use at least one of this command's routes
     */
    @Override
    public boolean testPermissionSilent(@NotNull CommandSender target) {
        return dispatcher.canUseAnyRoute(rootCommand, commandPlatformSupport.createSender(target));
    }
}
