package me.approximations.apxPlugin.commands;

import com.google.common.collect.ImmutableMap;
import me.approximations.apxPlugin.commands.utils.ApacheCommonsLangUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;

public class RegisteredCommand extends Command {
    public static final Map<Class<? extends CommandSender>, String> SENDER_FORMAT = ImmutableMap.of(
            ConsoleCommandSender.class, "CONSOLE",
            Player.class, "PLAYER"
    );
    private final RootCommand rootCommand;

    public RegisteredCommand(@NotNull RootCommand rootCommand, @NotNull String name, @NotNull String description, @NotNull List<String> aliases, @Nullable String permission) {
        super(name, description, null, aliases);
        if (permission != null) {
            setPermission(permission);
        }
        this.rootCommand = rootCommand;
    }

    @Override
    public boolean execute(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] args) {
        final String join = ApacheCommonsLangUtil.join(args, ' ');
        final RegisteredSubCommand subCommand = rootCommand.findSubCommand(join);

        if (subCommand == null) {
            commandSender.sendMessage(ChatColor.RED + "No such command");
            return false;
        }

        final Matcher matcher = subCommand.getAliasPattern().matcher(join);

        final Map<String, CommandArgument> arguments = subCommand.getArguments();

        final Class<?> senderType = subCommand.getSenderType();

        if (subCommand.getPermission() != null && !commandSender.hasPermission(subCommand.getPermission())) {
            commandSender.sendMessage(ChatColor.RED + "You do not have permission to execute this command!");
            return false;
        }

        if (!senderType.isAssignableFrom(commandSender.getClass())) {
            commandSender.sendMessage(ChatColor.RED + "You must be a " + SENDER_FORMAT.get(senderType) + " to execute this command!");
            return false;
        }

        if (arguments.isEmpty()) {
            subCommand.execute(commandSender);
            return true;
        }

        final List<Object> argumentValues = new ArrayList<>();
        for (final Map.Entry<String, CommandArgument> entry : arguments.entrySet()) {
            final String value = matcher.group(entry.getValue().getId());
            argumentValues.add(value);
        }

        subCommand.execute(commandSender, argumentValues);

        return true;
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        final Set<String> cmds = new HashSet<>();

        final int cmdIndex = Math.max(0, args.length - 1);
        final String currentArg = args[cmdIndex];
        final String argJoined = ApacheCommonsLangUtil.join(args, ' ');

        for (final RegisteredSubCommand subCommand : rootCommand.getSubCommands().values()) {
            if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                continue;
            }

            final String[] subAliasSplit = subCommand.getAlias().split(" ");

            if (subAliasSplit.length < args.length) continue;

            final String subAlias = subAliasSplit[cmdIndex];

            if (subCommand.matches(argJoined) || subCommand.partiallyMatches(argJoined)) {
                final CommandArgument indexArgument = subCommand.getArguments().get(subAlias);

                if (indexArgument != null) {
                    final CommandCompletionHandler completion = rootCommand.getCommandManager().getCompletion(indexArgument.getCompletionId());
                    if (completion != null) {
                        final Collection<String> completionResult = completion.handle(new CommandCompletionContext(sender));

                        for (final String s : completionResult) {
                            if (s.startsWith(currentArg)) {
                                cmds.add(s);
                            }
                        }
                    }
                    continue;
                }

                cmds.add(subAlias);
            }

        }

        return new ArrayList<>(cmds);
    }
}
