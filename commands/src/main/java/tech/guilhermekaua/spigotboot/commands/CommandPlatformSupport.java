package tech.guilhermekaua.spigotboot.commands;

public interface CommandPlatformSupport {
    CommandSenderHandle createSender(Object nativeSender);

    boolean isSenderType(Class<?> type);
}
