package tech.guilhermekaua.spigotboot.commands;

import org.bukkit.command.CommandSender;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.List;
import java.util.Map;

public interface CommandExecutionContext {
    Context getContext();

    BootPlugin getPlugin();

    CommandSender getSender();

    String getCommandLabel();

    String getInput();

    List<String> getArguments();

    Map<String, String> getParsedArguments();

    String getUsage();

    void sendMessage(String message);
}
