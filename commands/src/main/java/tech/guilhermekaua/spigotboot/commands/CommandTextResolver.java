package tech.guilhermekaua.spigotboot.commands;

public interface CommandTextResolver {
    String resolve(CommandTextResolutionContext context, String value);
}
