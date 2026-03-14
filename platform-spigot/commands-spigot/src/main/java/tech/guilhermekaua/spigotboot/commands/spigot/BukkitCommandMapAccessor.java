package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.Map;

public class BukkitCommandMapAccessor {
    public CommandMap getCommandMap() {
        ReflectiveOperationException lastException = null;

        try {
            PluginManager pluginManager = Bukkit.getPluginManager();
            Field field = findField(pluginManager.getClass(), "commandMap");
            if (field != null) {
                field.setAccessible(true);
                return (CommandMap) field.get(pluginManager);
            }
        } catch (ReflectiveOperationException e) {
            lastException = e;
        }

        try {
            Object server = Bukkit.getServer();
            Field field = findField(server.getClass(), "commandMap");
            if (field != null) {
                field.setAccessible(true);
                return (CommandMap) field.get(server);
            }
        } catch (ReflectiveOperationException e) {
            lastException = e;
        }

        throw new IllegalStateException("Failed to access Bukkit command map.", lastException);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Command> getKnownCommands(CommandMap commandMap) {
        try {
            Field field = findField(commandMap.getClass(), "knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Bukkit known commands.", e);
        }
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
