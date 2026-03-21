package tech.guilhermekaua.spigotboot.commands;

@FunctionalInterface
public interface CommandCompletionRegistryCustomizer {
    void customize(CommandCompletionRegistry registry);
}
