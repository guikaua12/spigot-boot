package tech.guilhermekaua.spigotboot.commands;

public interface CommandReplacementRegistry {
    void register(String key, String value);

    String replace(String value);
}
