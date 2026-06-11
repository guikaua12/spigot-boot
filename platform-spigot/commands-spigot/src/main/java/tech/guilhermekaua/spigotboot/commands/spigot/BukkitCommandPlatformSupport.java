package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.CommandSender;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

public class BukkitCommandPlatformSupport implements CommandPlatformSupport {
    @Override
    public CommandSenderHandle createSender(Object nativeSender) {
        if (!(nativeSender instanceof CommandSender)) {
            throw new IllegalArgumentException("Expected a Bukkit CommandSender but got " +
                    (nativeSender == null ? "null" : nativeSender.getClass().getName()) + ".");
        }
        return new BukkitCommandSender((CommandSender) nativeSender);
    }

    @Override
    public boolean isSenderType(Class<?> type) {
        return type != null && CommandSender.class.isAssignableFrom(type);
    }
}
