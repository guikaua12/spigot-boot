package me.approximations.apxPlugin.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Getter
public class RegisteredSubCommand {
    private final RootCommand rootCommand;
    private final Method method;
    private final String alias;
    private Pattern aliasPattern;
    private final String permission;
    private final String description;
    private final Class<?> senderType;
    private final Map<String, CommandArgument> arguments = new HashMap<>();
    private final ThreadLocal<Matcher> matcherThreadLocal = ThreadLocal.withInitial(() -> aliasPattern.matcher(""));

    public void execute(CommandSender commandSender, Object... args) {
        final Object[] arguments = new Object[args.length + 1];
        arguments[0] = commandSender;
        System.arraycopy(args, 0, arguments, 1, args.length);

        try {
            method.invoke(rootCommand, arguments);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute(CommandSender commandSender, List<Object> args) {
        execute(commandSender, args.toArray());
    }

    public void onRegister() {
        aliasPattern = subCommandToPattern(alias);

        for (Parameter p : method.getParameters()) {
            if (!p.isAnnotationPresent(me.approximations.apxPlugin.commands.annotations.CommandArgument.class))
                continue;

            final me.approximations.apxPlugin.commands.annotations.CommandArgument annotation = p.getAnnotation(me.approximations.apxPlugin.commands.annotations.CommandArgument.class);
            arguments.put("{" + annotation.value() + "}", new CommandArgument(annotation.value(), p, p.getType(), annotation.completion()));
        }
    }

    private Pattern subCommandToPattern(String subCommand) {
        final Matcher matcher = Pattern.compile("\\{(.+?)}").matcher(subCommand);

        while (matcher.find()) {
            String identifier = matcher.group(1);
            subCommand = subCommand.replace("{" + identifier + "}", "(?<" + identifier + ">\\S+)");
        }

        return Pattern.compile(subCommand);
    }

    public boolean matches(String input) {
        final Matcher matcher = matcherThreadLocal.get();
        matcher.reset(input);
        return matcher.matches();
    }

    public boolean partiallyMatches(String input) {
        final Matcher matcher = matcherThreadLocal.get();
        matcher.reset(input);
        return matcher.hitEnd();
    }
}
