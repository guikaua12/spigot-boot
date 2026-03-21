package tech.guilhermekaua.spigotboot.commands;

import java.util.Optional;

public interface CommandSenderHandle {
    String getName();

    String getIdentity();

    boolean hasPermission(String permission);

    void sendMessage(String message);

    <T> Optional<T> unwrap(Class<T> type);
}
