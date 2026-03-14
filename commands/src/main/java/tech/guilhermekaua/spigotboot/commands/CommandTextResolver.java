package tech.guilhermekaua.spigotboot.commands;

@FunctionalInterface
public interface CommandTextResolver {
    String resolve(CommandTextResolutionContext context, String value);
}
