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

import java.lang.reflect.Parameter;
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
        commandSender.sendMessage(rootCommand.getSubCommands().values().toString());
        final String join = ApacheCommonsLangUtil.join(args, ' ');
        final RegisteredSubCommand subCommand = rootCommand.findSubCommand(join);

        if (subCommand == null) {
            commandSender.sendMessage(ChatColor.RED + "No such command");
            return false;
        }

        final Matcher matcher = subCommand.getAliasPattern().matcher(join);

        if (!matcher.matches()) {
            commandSender.sendMessage(ChatColor.RED + "No such command");
            return false;
        }

        final List<Parameter> parameters = Arrays.asList(subCommand.getParameters());

        final Class<?> senderType = parameters.get(0).getType();
        if (!senderType.isAssignableFrom(commandSender.getClass())) {
            commandSender.sendMessage(ChatColor.RED + "You must be a " + SENDER_FORMAT.get(senderType) + " to execute this command!");
            return false;
        }

        if (parameters.size() < 2) {
            subCommand.execute(commandSender);
            return true;
        }

        final List<Object> arguments = new ArrayList<>();

        for (final Parameter parameter : parameters.subList(1, parameters.size())) {
            final String parameterId = parameter.getAnnotation(CommandArgument.class).value();
            final String group = matcher.group(parameterId);
            arguments.add(group);
        }

        subCommand.execute(commandSender, arguments);

        return true;
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        sender.sendMessage(alias);
        sender.sendMessage(Arrays.toString(args));

        final Set<String> cmds = new HashSet<>();

        final int cmdIndex = Math.max(0, args.length - 1);
        final String arg = ApacheCommonsLangUtil.join(args, ' ');

//        String argString = ApacheCommonsLangUtil.join(args, " ").toLowerCase(Locale.ENGLISH);

        for (final RegisteredSubCommand subCommand : rootCommand.getSubCommands().values()) {
            final Matcher matcher = subCommand.getAliasPattern().matcher(arg);

            if (matcher.matches() || matcher.hitEnd()) {
                cmds.add(subCommand.getAlias().split(" ")[cmdIndex]);
                continue;
            }
//            final String[] aliasSplit = subCommand.getAlias().split(" ");
//
//            if (aliasSplit.length < args.length) {
//                continue;
//            }
//
//            final String patternString = ApacheCommonsLangUtil.join(subCommand.getAliasPattern().pattern().split(" "), ' ', 0, cmdIndex);
//            final Pattern pattern = Pattern.compile(patternString);
//            final Matcher matcher = pattern.matcher(arg);
//
//            if (matcher.lookingAt()) {
//                cmds.add(aliasSplit[cmdIndex]);
//            }
        }

        return new ArrayList<>(cmds);
    }
}
