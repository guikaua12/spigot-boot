package tech.guilhermekaua.spigotboot.commands;

public interface CommandCompletionRegistry {
    void register(String id, CommandCompletionProvider provider);

    CommandCompletionProvider resolve(String id);
}
