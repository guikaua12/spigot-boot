package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

import java.util.Objects;
import java.util.Optional;

public class BukkitCommandSender implements CommandSenderHandle {
    private final CommandSender sender;

    public BukkitCommandSender(CommandSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender cannot be null.");
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public String getIdentity() {
        if (sender instanceof Entity) {
            return ((Entity) sender).getUniqueId().toString();
        }

        String name = sender.getName();
        return sender.getClass().getName() + ":" + (name == null ? "" : name);
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type == null || !type.isInstance(sender)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(sender));
    }
}
