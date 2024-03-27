package me.approximations.apxPlugin.commands;

import lombok.Getter;
import me.approximations.apxPlugin.commands.utils.MethodFormatUtils;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public abstract class RootCommand {
    private final Map<String, RegisteredSubCommand> subCommands = new HashMap<>();

    public void registerSubCommand(Method method) {
        validateSubCommand(method);

        final SubCommand alias = method.getAnnotation(SubCommand.class);

        final CommandPermission permission = method.getAnnotation(CommandPermission.class);
        final CommandDescription description = method.getAnnotation(CommandDescription.class);

        final RegisteredSubCommand registeredSubCommand = new RegisteredSubCommand(
                this, method,
                alias.value(),
                permission != null ? permission.value() : null,
                description != null ? description.value() : "",
                method.getParameters()
        );
        subCommands.put(alias.value(), registeredSubCommand);

        registeredSubCommand.onRegister();
    }

    private void validateSubCommand(Method method) {
        final SubCommand subCommandAnnotation = method.getAnnotation(SubCommand.class);
        if (subCommandAnnotation == null) return;

        final List<Parameter> parameters = Arrays.asList(method.getParameters());

        if (parameters.isEmpty()) {
            throw new IllegalStateException("SubCommand " + MethodFormatUtils.formatMethod(method) + " must have at least one parameter");
        }

        if (!CommandSender.class.isAssignableFrom(parameters.get(0).getType())) {
            throw new IllegalStateException("SubCommand " + MethodFormatUtils.formatMethod(method) + " first parameter must be a CommandSender");
        }

        final Set<String> variables = getVariables(subCommandAnnotation.value());


        if (parameters.size() > 1) {
            final List<Parameter> argumentParameters = parameters.subList(1, parameters.size());
            if (variables.size() != argumentParameters.size()) {
                throw new IllegalStateException("SubCommand " + MethodFormatUtils.formatMethod(method) + " must have the same number of variables as parameters");
            }

            for (final Parameter parameter : argumentParameters) {
                if (!parameter.isAnnotationPresent(CommandArgument.class)) {
                    throw new IllegalStateException("SubCommand " + MethodFormatUtils.formatMethod(method) + " parameter " + parameter.getName() + " must have a CommandArgument annotation");
                }
            }
        }
    }

    private static Set<String> getVariables(String subCommand) {
        final Set<String> variables = new HashSet<>();
        final Matcher matcher = Pattern.compile("\\{(.+?)}").matcher(subCommand);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }

    public void onRegister(CommandManager commandManager) {
        final Class<? extends RootCommand> clazz = getClass();

        final CommandAlias alias = clazz.getAnnotation(CommandAlias.class);
        if (alias == null) {
            throw new IllegalArgumentException("Root command must have a CommandAlias annotation");
        }
        final List<String> aliases = Arrays.asList(alias.value().split("\\|"));

        final CommandPermission permission = clazz.getAnnotation(CommandPermission.class);
        final CommandDescription description = clazz.getAnnotation(CommandDescription.class);


        final RegisteredCommand registeredCommand = new RegisteredCommand(
                this,
                aliases.get(0),
                description != null ? description.value() : "",
                aliases.subList(1, aliases.size()),
                permission != null ? permission.value() : null
        );
        final CommandMap commandMap = commandManager.getCommandMap();
        commandMap.register(aliases.get(0), registeredCommand);

        for (Method method : clazz.getDeclaredMethods()) {
            registerSubCommand(method);
        }

    }

    public RegisteredSubCommand findSubCommand(String input) {
        for (final Map.Entry<String, RegisteredSubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().matches(input)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
