package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DefaultCommandExecutionContext implements CommandExecutionContext {
    private final Context context;
    private final CommandSenderHandle sender;
    private final String commandLabel;
    private final String input;
    private final List<String> arguments;
    private final Map<String, String> parsedArguments;
    private final String usage;

    public DefaultCommandExecutionContext(Context context,
                                          CommandSenderHandle sender,
                                          String commandLabel,
                                          String input,
                                          List<String> arguments,
                                          Map<String, String> parsedArguments,
                                          String usage) {
        this.context = context;
        this.sender = sender;
        this.commandLabel = commandLabel;
        this.input = input;
        this.arguments = Collections.unmodifiableList(arguments);
        this.parsedArguments = Collections.unmodifiableMap(parsedArguments);
        this.usage = usage == null ? "" : usage;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public BootPlugin getPlugin() {
        return context.getPlugin();
    }

    @Override
    public CommandSenderHandle getSender() {
        return sender;
    }

    @Override
    public String getCommandLabel() {
        return commandLabel;
    }

    @Override
    public String getInput() {
        return input;
    }

    @Override
    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public Map<String, String> getParsedArguments() {
        return parsedArguments;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public void sendMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            sender.sendMessage(message);
        }
    }
}
