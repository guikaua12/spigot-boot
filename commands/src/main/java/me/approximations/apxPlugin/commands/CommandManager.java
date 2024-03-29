package me.approximations.apxPlugin.commands;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final Plugin plugin;
    @Getter
    private CommandMap commandMap;
    private Map<String, Command> knownCommands;
    private final Map<String, CommandCompletionHandler> completions = new HashMap<>();

    public CommandManager(Plugin plugin) {
        this.plugin = plugin;
        this.commandMap = this.hookCommandMap();
    }

    public void registerCompletion(String id, CommandCompletionHandler handler) {
        completions.put(id, handler);
    }

    public CommandCompletionHandler getCompletion(String id) {
        return completions.get(id);
    }

    private CommandMap hookCommandMap() {
        CommandMap commandMap = null;
        try {
            Server server = Bukkit.getServer();
            Method getCommandMap = server.getClass().getDeclaredMethod("getCommandMap");
            getCommandMap.setAccessible(true);
            commandMap = (CommandMap) getCommandMap.invoke(server);
            Field knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
            //noinspection unchecked
            this.knownCommands = (Map<String, Command>) knownCommands.get(commandMap);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get Command Map. ACF will not function.");
        }
        return commandMap;
    }

    public void registerCommand(RootCommand rootCommand) {
        rootCommand.onRegister(this);
    }

    public RegisteredCommand getCommand(String alias) {
        return (RegisteredCommand) commandMap.getCommand(alias);
    }
}
